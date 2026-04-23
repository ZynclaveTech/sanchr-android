package com.sanchr.core.callengine

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.sanchr.proto.calling.CallOffer
import com.sanchr.proto.calling.CallResponse
import com.sanchr.proto.calling.CallSignal
import com.sanchr.proto.calling.CallSignalingServiceClient
import com.sanchr.proto.calling.EndCallRequest
import com.sanchr.proto.calling.GetTurnCredentialsRequest
import com.sanchr.proto.calling.TurnCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

sealed interface CallState {
    data object Idle : CallState

    data class Outgoing(
        val callId: String,
        val recipientId: String,
        val recipientName: String,
        val isVideo: Boolean,
    ) : CallState

    data class Incoming(
        val callId: String,
        val callerId: String,
        val callerName: String,
        val isVideo: Boolean,
        val sdpOffer: String,
    ) : CallState

    data class Ringing(
        val callId: String,
        val recipientId: String,
        val recipientName: String,
        val isVideo: Boolean,
    ) : CallState

    data class Active(
        val callId: String,
        val remoteUserId: String,
        val remoteUserName: String,
        val isVideo: Boolean,
        val startTime: Long,
    ) : CallState

    data class Reconnecting(
        val callId: String,
        val remoteUserId: String,
        val remoteUserName: String,
        val isVideo: Boolean,
    ) : CallState

    data class Ended(
        val callId: String,
        val reason: EndReason,
    ) : CallState

    enum class EndReason {
        NORMAL,
        BUSY,
        DECLINED,
        FAILED,
        TIMEOUT,
        NETWORK_ERROR,
    }
}

@Singleton
class CallManager
    @Inject
    constructor(
        private val webRTCClient: WebRTCClient,
        private val callServiceClient: CallSignalingServiceClient,
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "CallManager"
            private const val CALL_TIMEOUT_MS = 60_000L
        }

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        private val _callState = MutableStateFlow<CallState>(CallState.Idle)
        val callState: StateFlow<CallState> = _callState.asStateFlow()

        private val _isMuted = MutableStateFlow(false)
        val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

        private val _isSpeakerOn = MutableStateFlow(false)
        val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

        private val _isVideoEnabled = MutableStateFlow(true)
        val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()

        private val _callDuration = MutableStateFlow(0L)
        val callDuration: StateFlow<Long> = _callDuration.asStateFlow()

        private val _callType = MutableStateFlow("voice")
        val callType: StateFlow<String> = _callType.asStateFlow()

        private val _outgoingSignals = MutableSharedFlow<CallSignal>(extraBufferCapacity = 64)

        private val _callEvents = MutableSharedFlow<CallManagerEvent>(extraBufferCapacity = 16)
        val callEvents: SharedFlow<CallManagerEvent> = _callEvents.asSharedFlow()

        private var durationJob: Job? = null
        private var signalingJob: Job? = null
        private var timeoutJob: Job? = null
        private var webRTCEventsJob: Job? = null

        init {
            observeWebRTCEvents()
        }

        // -----------------------------------------------------------------------
        // Outgoing call
        // -----------------------------------------------------------------------

        suspend fun startCall(
            recipientId: String,
            recipientName: String,
            isVideo: Boolean,
        ) {
            if (_callState.value !is CallState.Idle) {
                Log.w(TAG, "Cannot start call: already in a call")
                return
            }

            val callId = UUID.randomUUID().toString()
            _callType.value = if (isVideo) "video" else "voice"
            _isVideoEnabled.value = isVideo

            _callState.value =
                CallState.Outgoing(
                    callId = callId,
                    recipientId = recipientId,
                    recipientName = recipientName,
                    isVideo = isVideo,
                )

            try {
                configureAudioSession(isVideo)

                // 1. Fetch TURN credentials
                val turnCreds =
                    callServiceClient.getTurnCredentials(
                        GetTurnCredentialsRequest(callId = callId),
                    )
                val iceServers = buildIceServers(turnCreds)

                // 2. Configure WebRTC peer connection
                webRTCClient.configure(iceServers)

                // 3. Start local media
                webRTCClient.startLocalMedia(isVideo)

                // 4. Create SDP offer
                val offer = webRTCClient.createOffer()
                webRTCClient.setLocalDescription(offer)

                // 5. Send offer to server via initiateCall
                val response: CallResponse =
                    callServiceClient.initiateCall(
                        CallOffer(
                            callId = callId,
                            calleeId = recipientId,
                            callType = if (isVideo) "video" else "audio",
                            sdpOffer = offer.description,
                            timestamp = System.currentTimeMillis(),
                        ),
                    )

                if (!response.accepted && response.sdpAnswer.isEmpty()) {
                    // Server rejected or callee busy
                    handleCallEnded(callId, CallState.EndReason.BUSY)
                    return
                }

                // 6. Move to ringing state
                _callState.value =
                    CallState.Ringing(
                        callId = callId,
                        recipientId = recipientId,
                        recipientName = recipientName,
                        isVideo = isVideo,
                    )

                // 7. Open bidirectional signaling stream
                startSignalingStream(callId)

                // 8. Start call timeout
                startCallTimeout(callId)

                // If server immediately returned an answer, process it
                if (response.sdpAnswer.isNotEmpty()) {
                    val remoteSdp =
                        SessionDescription(
                            SessionDescription.Type.ANSWER,
                            response.sdpAnswer,
                        )
                    webRTCClient.setRemoteDescription(remoteSdp)
                    onCallConnected(callId, recipientId, recipientName, isVideo)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start call", e)
                handleCallEnded(callId, CallState.EndReason.FAILED)
            }
        }

        // -----------------------------------------------------------------------
        // Incoming call
        // -----------------------------------------------------------------------

        fun handleIncomingCall(
            callId: String,
            callerId: String,
            callerName: String,
            sdpOffer: String,
            isVideo: Boolean,
        ) {
            if (_callState.value !is CallState.Idle) {
                // Already in a call, send busy
                scope.launch {
                    try {
                        callServiceClient.endCall(
                            EndCallRequest(callId = callId, reason = "busy"),
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send busy signal", e)
                    }
                }
                return
            }

            _callType.value = if (isVideo) "video" else "voice"
            _isVideoEnabled.value = isVideo

            _callState.value =
                CallState.Incoming(
                    callId = callId,
                    callerId = callerId,
                    callerName = callerName,
                    isVideo = isVideo,
                    sdpOffer = sdpOffer,
                )

            _callEvents.tryEmit(CallManagerEvent.IncomingCall(callId, callerName, isVideo))
        }

        suspend fun answerCall() {
            val state = _callState.value
            if (state !is CallState.Incoming) {
                Log.w(TAG, "Cannot answer: not in incoming state")
                return
            }

            val callId = state.callId
            val isVideo = state.isVideo

            try {
                configureAudioSession(isVideo)

                // 1. Fetch TURN credentials
                val turnCreds =
                    callServiceClient.getTurnCredentials(
                        GetTurnCredentialsRequest(callId = callId),
                    )
                val iceServers = buildIceServers(turnCreds)

                // 2. Configure WebRTC
                webRTCClient.configure(iceServers)

                // 3. Set remote description (the offer)
                val remoteSdp =
                    SessionDescription(
                        SessionDescription.Type.OFFER,
                        state.sdpOffer,
                    )
                webRTCClient.setRemoteDescription(remoteSdp)

                // 4. Start local media
                webRTCClient.startLocalMedia(isVideo)

                // 5. Create answer
                val answer = webRTCClient.createAnswer()
                webRTCClient.setLocalDescription(answer)

                // 6. Open signaling stream and send answer
                startSignalingStream(callId)

                _outgoingSignals.emit(
                    CallSignal.SdpAnswer(
                        callId = callId,
                        sdp = answer.description,
                    ),
                )

                // 7. Transition to active
                onCallConnected(callId, state.callerId, state.callerName, isVideo)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to answer call", e)
                handleCallEnded(callId, CallState.EndReason.FAILED)
            }
        }

        fun declineCall() {
            val state = _callState.value
            if (state !is CallState.Incoming) return

            scope.launch {
                try {
                    _outgoingSignals.emit(
                        CallSignal.Control(
                            callId = state.callId,
                            action = "declined",
                        ),
                    )
                    callServiceClient.endCall(
                        EndCallRequest(callId = state.callId, reason = "declined"),
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decline call", e)
                }
                handleCallEnded(state.callId, CallState.EndReason.DECLINED)
            }
        }

        suspend fun endCall() {
            val currentState = _callState.value
            if (currentState is CallState.Idle || currentState is CallState.Ended) return

            val callId =
                when (currentState) {
                    is CallState.Outgoing -> currentState.callId
                    is CallState.Incoming -> currentState.callId
                    is CallState.Ringing -> currentState.callId
                    is CallState.Active -> currentState.callId
                    is CallState.Reconnecting -> currentState.callId
                    else -> return
                }

            try {
                _outgoingSignals.emit(
                    CallSignal.Control(
                        callId = callId,
                        action = "ended",
                    ),
                )
                callServiceClient.endCall(
                    EndCallRequest(callId = callId, reason = "normal"),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to end call on server", e)
            }

            handleCallEnded(callId, CallState.EndReason.NORMAL)
        }

        // -----------------------------------------------------------------------
        // In-call controls
        // -----------------------------------------------------------------------

        fun toggleMute() {
            val muted = webRTCClient.toggleMute()
            _isMuted.value = muted
        }

        fun toggleSpeaker() {
            val speaker = webRTCClient.toggleSpeaker()
            _isSpeakerOn.value = speaker
        }

        fun toggleVideo() {
            val enabled = webRTCClient.toggleVideo()
            _isVideoEnabled.value = enabled
        }

        fun switchCamera() {
            webRTCClient.switchCamera()
        }

        // -----------------------------------------------------------------------
        // Signaling stream
        // -----------------------------------------------------------------------

        private fun startSignalingStream(callId: String) {
            signalingJob?.cancel()
            signalingJob =
                scope.launch {
                    try {
                        val incomingSignals = callServiceClient.callStream(_outgoingSignals)
                        incomingSignals.collect { signal ->
                            handleSignalingMessage(callId, signal)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Signaling stream error", e)
                        val state = _callState.value
                        if (state !is CallState.Idle && state !is CallState.Ended) {
                            handleCallEnded(callId, CallState.EndReason.NETWORK_ERROR)
                        }
                    }
                }
        }

        private suspend fun handleSignalingMessage(
            callId: String,
            signal: CallSignal,
        ) {
            when (signal) {
                is CallSignal.SdpAnswer -> {
                    Log.d(TAG, "Received SDP answer for $callId")
                    val remoteSdp =
                        SessionDescription(
                            SessionDescription.Type.ANSWER,
                            signal.sdp,
                        )
                    webRTCClient.setRemoteDescription(remoteSdp)

                    // Transition to active if we were ringing
                    val state = _callState.value
                    if (state is CallState.Ringing) {
                        onCallConnected(
                            callId,
                            state.recipientId,
                            state.recipientName,
                            state.isVideo,
                        )
                    }
                }

                is CallSignal.IceCandidate -> {
                    Log.d(TAG, "Received ICE candidate for $callId")
                    val candidate =
                        IceCandidate(
                            signal.sdpMid,
                            signal.sdpMLineIndex,
                            signal.candidate,
                        )
                    webRTCClient.addIceCandidate(candidate)
                }

                is CallSignal.Control -> {
                    Log.d(TAG, "Received control: ${signal.action} for $callId")
                    when (signal.action) {
                        "accepted" -> {
                            // Remote accepted, waiting for SDP answer
                        }

                        "declined" -> {
                            handleCallEnded(callId, CallState.EndReason.DECLINED)
                        }

                        "ended" -> {
                            handleCallEnded(callId, CallState.EndReason.NORMAL)
                        }

                        "busy" -> {
                            handleCallEnded(callId, CallState.EndReason.BUSY)
                        }

                        "ringing" -> {
                            val state = _callState.value
                            if (state is CallState.Outgoing) {
                                _callState.value =
                                    CallState.Ringing(
                                        callId = state.callId,
                                        recipientId = state.recipientId,
                                        recipientName = state.recipientName,
                                        isVideo = state.isVideo,
                                    )
                            }
                        }
                    }
                }
            }
        }

        // -----------------------------------------------------------------------
        // WebRTC events
        // -----------------------------------------------------------------------

        private fun observeWebRTCEvents() {
            webRTCEventsJob =
                scope.launch {
                    webRTCClient.events.collect { event ->
                        when (event) {
                            is WebRTCEvent.LocalIceCandidate -> {
                                val state = _callState.value
                                val callId = extractCallId(state) ?: return@collect

                                _outgoingSignals.emit(
                                    CallSignal.IceCandidate(
                                        callId = callId,
                                        candidate = event.candidate.sdp,
                                        sdpMid = event.candidate.sdpMid,
                                        sdpMLineIndex = event.candidate.sdpMLineIndex,
                                    ),
                                )
                            }

                            is WebRTCEvent.ConnectionStateChanged -> {
                                handleConnectionStateChange(event.state)
                            }

                            is WebRTCEvent.RemoteVideoTrackReceived -> {
                                // Track received; the UI will observe and attach renderers
                            }

                            is WebRTCEvent.SignalingStateChanged -> {
                                Log.d(TAG, "Signaling state: ${event.state}")
                            }

                            is WebRTCEvent.IceGatheringComplete -> {
                                Log.d(TAG, "ICE gathering complete")
                            }

                            is WebRTCEvent.PeerConnectionError -> {
                                Log.e(TAG, "PeerConnection error: ${event.message}")
                            }
                        }
                    }
                }
        }

        private fun handleConnectionStateChange(state: PeerConnection.IceConnectionState) {
            val currentState = _callState.value
            val callId = extractCallId(currentState) ?: return

            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED,
                -> {
                    if (currentState is CallState.Reconnecting) {
                        _callState.value =
                            CallState.Active(
                                callId = callId,
                                remoteUserId = currentState.remoteUserId,
                                remoteUserName = currentState.remoteUserName,
                                isVideo = currentState.isVideo,
                                startTime = System.currentTimeMillis(),
                            )
                    }
                }

                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    if (currentState is CallState.Active) {
                        _callState.value =
                            CallState.Reconnecting(
                                callId = callId,
                                remoteUserId = currentState.remoteUserId,
                                remoteUserName = currentState.remoteUserName,
                                isVideo = currentState.isVideo,
                            )
                    }
                }

                PeerConnection.IceConnectionState.FAILED -> {
                    handleCallEnded(callId, CallState.EndReason.NETWORK_ERROR)
                }

                else -> {}
            }
        }

        // -----------------------------------------------------------------------
        // Helpers
        // -----------------------------------------------------------------------

        private fun onCallConnected(
            callId: String,
            remoteUserId: String,
            remoteUserName: String,
            isVideo: Boolean,
        ) {
            cancelTimeout()

            _callState.value =
                CallState.Active(
                    callId = callId,
                    remoteUserId = remoteUserId,
                    remoteUserName = remoteUserName,
                    isVideo = isVideo,
                    startTime = System.currentTimeMillis(),
                )

            startDurationTimer()
            _callEvents.tryEmit(CallManagerEvent.CallConnected(callId))
        }

        private fun handleCallEnded(
            callId: String,
            reason: CallState.EndReason,
        ) {
            durationJob?.cancel()
            durationJob = null
            signalingJob?.cancel()
            signalingJob = null
            cancelTimeout()

            webRTCClient.close()
            resetAudioSession()

            _callState.value = CallState.Ended(callId = callId, reason = reason)
            _callEvents.tryEmit(CallManagerEvent.CallEnded(callId, reason))

            // Reset to idle after brief delay so UI can show ended state
            scope.launch {
                delay(2_000)
                if (_callState.value is CallState.Ended) {
                    _callState.value = CallState.Idle
                    resetCallControls()
                }
            }
        }

        private fun resetCallControls() {
            _isMuted.value = false
            _isSpeakerOn.value = false
            _isVideoEnabled.value = true
            _callDuration.value = 0L
            _callType.value = "voice"
        }

        private fun startDurationTimer() {
            durationJob?.cancel()
            _callDuration.value = 0L
            durationJob =
                scope.launch {
                    while (true) {
                        delay(1_000)
                        _callDuration.update { it + 1 }
                    }
                }
        }

        private fun startCallTimeout(callId: String) {
            timeoutJob?.cancel()
            timeoutJob =
                scope.launch {
                    delay(CALL_TIMEOUT_MS)
                    val state = _callState.value
                    if (state is CallState.Outgoing || state is CallState.Ringing) {
                        Log.w(TAG, "Call $callId timed out")
                        try {
                            callServiceClient.endCall(
                                EndCallRequest(callId = callId, reason = "timeout"),
                            )
                        } catch (_: Exception) {
                        }
                        handleCallEnded(callId, CallState.EndReason.TIMEOUT)
                    }
                }
        }

        private fun cancelTimeout() {
            timeoutJob?.cancel()
            timeoutJob = null
        }

        private fun configureAudioSession(isVideo: Boolean) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = isVideo
            _isSpeakerOn.value = isVideo
        }

        private fun resetAudioSession() {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        }

        private val audioManager: AudioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        private fun buildIceServers(creds: TurnCredentials): List<PeerConnection.IceServer> {
            val servers = mutableListOf<PeerConnection.IceServer>()

            // Always include a public STUN server as fallback
            servers.add(
                PeerConnection.IceServer
                    .builder("stun:stun.l.google.com:19302")
                    .createIceServer(),
            )

            // Add TURN servers from credentials
            if (creds.urls.isNotEmpty()) {
                servers.add(
                    PeerConnection.IceServer
                        .builder(creds.urls)
                        .setUsername(creds.username)
                        .setPassword(creds.credential)
                        .createIceServer(),
                )
            }

            return servers
        }

        private fun extractCallId(state: CallState): String? =
            when (state) {
                is CallState.Outgoing -> state.callId
                is CallState.Incoming -> state.callId
                is CallState.Ringing -> state.callId
                is CallState.Active -> state.callId
                is CallState.Reconnecting -> state.callId
                is CallState.Ended -> state.callId
                is CallState.Idle -> null
            }
    }

sealed interface CallManagerEvent {
    data class IncomingCall(
        val callId: String,
        val callerName: String,
        val isVideo: Boolean,
    ) : CallManagerEvent

    data class CallConnected(
        val callId: String,
    ) : CallManagerEvent

    data class CallEnded(
        val callId: String,
        val reason: CallState.EndReason,
    ) : CallManagerEvent
}
