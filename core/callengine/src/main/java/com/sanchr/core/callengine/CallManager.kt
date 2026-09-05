package com.sanchr.core.callengine

import android.util.Log
import com.sanchr.core.callengine.signaling.CallPayloadException
import com.sanchr.core.callengine.signaling.CallSignalingCrypto
import com.sanchr.core.callengine.signaling.IceCandidateCodec
import com.sanchr.core.common.calls.CallLifecycleSignal
import com.sanchr.core.common.calls.CallPeerNames
import com.sanchr.core.common.calls.IncomingCallEvents
import com.sanchr.core.common.calls.IncomingCallOffer
import com.sanchr.core.common.calls.IncomingCallPush
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.calling.CallOffer
import com.sanchr.proto.calling.CallSignal
import com.sanchr.proto.calling.CallSignalPayload
import com.sanchr.proto.calling.CallSignalingServiceClient
import com.sanchr.proto.calling.EndCallRequest
import com.sanchr.proto.calling.GetTurnCredentialsRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
        /** The decrypted, fingerprint-verified offer SDP. */
        val sdpOffer: String,
        /** The caller's Signal device: where the answer must be encrypted to. */
        val callerDevice: Int,
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

/**
 * Drives a call end to end over `CallSignalingService`, the same protocol
 * iOS speaks (`Platform/Calls/CallManager.swift`):
 *
 *  - The SDP offer is sealed per recipient device ([CallSignalingCrypto]) and
 *    sent with `InitiateCall`; the server never sees SDP.
 *  - The callee learns of the call from a `CallOfferEvent` on the message
 *    stream ([onCallOffer]), decrypts and verifies it, and answers with an
 *    `encrypted_sdp_answer` addressed to the caller's device.
 *  - Both sides open `CallStream` with a `CallJoin` as the first frame, then
 *    exchange JSON ICE candidates and control actions, with a `ping` every
 *    20 s and up to five reconnects.
 *  - Terminal state also arrives as `CallLifecycleEvent`s ([onCallLifecycle]),
 *    which are applied like control actions.
 */
@Singleton
class CallManager internal constructor(
    private val webRTCClient: WebRTCClient,
    private val callServiceClient: CallSignalingServiceClient,
    private val signalingCrypto: CallSignalingCrypto,
    private val signalSessionManager: SignalSessionManager,
    private val sessionManager: SessionManager,
    private val peerNames: CallPeerNames,
    private val platform: CallPlatform,
    /** Main-thread scope in production; a test scope in tests so nothing outlives them. */
    private val scope: CoroutineScope,
) : IncomingCallEvents {
    @Inject
    constructor(
        webRTCClient: WebRTCClient,
        callServiceClient: CallSignalingServiceClient,
        signalingCrypto: CallSignalingCrypto,
        signalSessionManager: SignalSessionManager,
        sessionManager: SessionManager,
        peerNames: CallPeerNames,
        platform: CallPlatform,
    ) : this(
        webRTCClient,
        callServiceClient,
        signalingCrypto,
        signalSessionManager,
        sessionManager,
        peerNames,
        platform,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    companion object {
        private const val TAG = "CallManager"
        private const val CALL_TIMEOUT_MS = 60_000L
        private const val ENDED_LINGER_MS = 2_000L
        private const val OFFER_MAX_AGE_SECS = 30.0

        /** iOS allows two minutes for an offer that a push announced first. */
        private const val PUSHED_OFFER_MAX_AGE_SECS = 120.0
        private const val OFFER_WAIT_MS = 15_000L
        private const val OFFER_POLL_MS = 100L
        private const val ANSWER_MAX_AGE_SECS = 30.0
    }

    private val stream =
        CallStream(
            client = callServiceClient,
            scope = scope,
            localDeviceId = ::localDeviceId,
            isMuted = { _isMuted.value },
            isCurrent = { currentCallId() == it },
            onSignal = ::handleSignal,
            onFailed = { callId ->
                endCallOnServer(callId, "failed")
                handleCallEnded(callId, CallState.EndReason.NETWORK_ERROR)
            },
        )

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

    private val videoUpgrade =
        VideoUpgrade(
            webRTCClient = webRTCClient,
            platform = platform,
            signalingCrypto = signalingCrypto,
            scope = scope,
            send = { callId, payload -> stream.send(callId, payload) },
            peer = { peerId?.let { it to peerDevice } },
            isActiveCall = { callId -> (_callState.value as? CallState.Active)?.callId == callId },
            callType = _callType,
            isVideoEnabled = _isVideoEnabled,
            isSpeakerOn = _isSpeakerOn,
        )

    /** See [VideoUpgrade] for the mid-call voice→video handshake these expose. */
    val peerVideoEnabled: StateFlow<Boolean> get() = videoUpgrade.peerVideoEnabled
    val hasRemoteVideoTrack: StateFlow<Boolean> get() = videoUpgrade.hasRemoteVideoTrack
    val incomingVideoUpgradeRequest: StateFlow<Boolean> get() = videoUpgrade.incomingRequest
    val outgoingVideoUpgradePending: StateFlow<Boolean> get() = videoUpgrade.outgoingPending

    private val _callEvents = MutableSharedFlow<CallManagerEvent>(extraBufferCapacity = 16)
    val callEvents: SharedFlow<CallManagerEvent> = _callEvents.asSharedFlow()

    /** The peer's user id for the current call; where answers and stream frames are addressed. */
    private var peerId: String? = null

    /** The peer's Signal device id, learned from the offer (callee side) or the answer (caller side). */
    private var peerDevice: Int = 0

    private var durationJob: Job? = null
    private var timeoutJob: Job? = null
    private var webRTCEventsJob: Job? = null

    init {
        observeWebRTCEvents()
    }

    // ------------------------------------------------------------------
    // Outgoing
    // ------------------------------------------------------------------

    suspend fun startCall(
        recipientId: String,
        recipientName: String,
        isVideo: Boolean,
    ) {
        if (_callState.value !is CallState.Idle) {
            Log.w(TAG, "Cannot start call: already in a call")
            return
        }
        val callType = if (isVideo) "video" else "voice"
        _callType.value = callType
        _isVideoEnabled.value = isVideo
        peerId = recipientId
        peerDevice = 0
        _callState.value = CallState.Outgoing(callId = "", recipientId = recipientId, recipientName = recipientName, isVideo = isVideo)

        try {
            platform.configureAudioSession(isVideo)
            _isSpeakerOn.value = isVideo
            webRTCClient.configure(platform.buildIceServers(callServiceClient.getTurnCredentials(GetTurnCredentialsRequest)))
            webRTCClient.startLocalMedia(isVideo)
            val offer = webRTCClient.createOffer()
            webRTCClient.setLocalDescription(offer)

            val deviceOffers = signalingCrypto.sealOffer(offer.description, recipientId)
            val response = callServiceClient.initiateCall(CallOffer(recipientId, callType, deviceOffers))
            val callId = response.callId
            Log.i(TAG, "Call initiated: $callId status=${response.status}")

            when (response.status) {
                "busy" -> {
                    handleCallEnded(callId, CallState.EndReason.BUSY)
                    return
                }
                "unavailable" -> {
                    handleCallEnded(callId, CallState.EndReason.FAILED)
                    return
                }
            }
            _callState.value = CallState.Ringing(callId, recipientId, recipientName, isVideo)
            stream.open(callId, CallStream.ROLE_CALLER)
            startCallTimeout(callId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call", e)
            handleCallEnded(currentCallId() ?: "", CallState.EndReason.FAILED)
        }
    }

    // ------------------------------------------------------------------
    // Incoming (from the message stream)
    // ------------------------------------------------------------------

    /**
     * The push arrives before the offer when the app is in the
     * background: ring now with an empty SDP, and let [onCallOffer]
     * fill it in once the stream replays the `CallOfferEvent`.
     * [answerCall] waits for it.
     */
    override suspend fun onCallPush(push: IncomingCallPush) {
        if (currentCallId() == push.callId) return
        if (_callState.value !is CallState.Idle) {
            Log.w(TAG, "Rejecting pushed call ${push.callId} as busy")
            endCallOnServer(push.callId, "busy")
            return
        }
        val isVideo = push.callType == "video"
        val callerName = runCatching { peerNames.displayNameFor(push.callerId) }.getOrDefault(push.callerId)
        peerId = push.callerId
        peerDevice = CallSignalingCrypto.resolveDevice(push.callerDevice)
        _callType.value = if (isVideo) "video" else "voice"
        _isVideoEnabled.value = isVideo
        _callState.value =
            CallState.Incoming(
                callId = push.callId,
                callerId = push.callerId,
                callerName = callerName,
                isVideo = isVideo,
                sdpOffer = "",
                callerDevice = peerDevice,
            )
        _callEvents.tryEmit(CallManagerEvent.IncomingCall(push.callId, callerName, isVideo))
        platform.startIncomingCallService(push.callId, push.callerId, callerName, isVideo)
        startRingTimeout(push.callId)
    }

    override suspend fun onCallOffer(offer: IncomingCallOffer) {
        val current = _callState.value
        if (current is CallState.Incoming && current.callId == offer.callId && current.sdpOffer.isEmpty()) {
            // The push rang first; this is the SDP it promised.
            val payload =
                try {
                    signalingCrypto.open(offer.encryptedSdpPayload, offer.callerId, offer.callerDevice, PUSHED_OFFER_MAX_AGE_SECS)
                } catch (e: CallPayloadException) {
                    Log.e(TAG, "Offer for pushed call ${offer.callId} rejected: ${e.message}")
                    runCatching {
                        signalSessionManager.resetSession(
                            offer.callerId,
                            CallSignalingCrypto.resolveDevice(offer.callerDevice),
                        )
                    }
                    endCallOnServer(offer.callId, "failed")
                    handleCallEnded(offer.callId, CallState.EndReason.FAILED)
                    return
                }
            peerDevice = CallSignalingCrypto.resolveDevice(offer.callerDevice)
            _callState.value = current.copy(sdpOffer = payload.sdp, callerDevice = peerDevice)
            Log.i(TAG, "SDP for pushed call ${offer.callId} received")
            return
        }
        if (currentCallId() == offer.callId) {
            Log.i(TAG, "Duplicate offer for current call ${offer.callId}")
            return
        }
        if (current !is CallState.Idle) {
            Log.w(TAG, "Rejecting offer ${offer.callId} as busy")
            endCallOnServer(offer.callId, "busy")
            return
        }
        val payload =
            try {
                signalingCrypto.open(offer.encryptedSdpPayload, offer.callerId, offer.callerDevice, OFFER_MAX_AGE_SECS)
            } catch (e: CallPayloadException) {
                Log.e(TAG, "Incoming offer ${offer.callId} rejected: ${e.message}")
                // A session that cannot decrypt the caller's PreKey message
                // is dead weight; iOS drops it too, so the next offer starts clean.
                runCatching { signalSessionManager.resetSession(offer.callerId, CallSignalingCrypto.resolveDevice(offer.callerDevice)) }
                return
            }
        val isVideo = offer.callType == "video"
        val callerName = runCatching { peerNames.displayNameFor(offer.callerId) }.getOrDefault(offer.callerId)
        peerId = offer.callerId
        peerDevice = CallSignalingCrypto.resolveDevice(offer.callerDevice)
        _callType.value = if (isVideo) "video" else "voice"
        _isVideoEnabled.value = isVideo
        _callState.value =
            CallState.Incoming(
                callId = offer.callId,
                callerId = offer.callerId,
                callerName = callerName,
                isVideo = isVideo,
                sdpOffer = payload.sdp,
                callerDevice = peerDevice,
            )
        _callEvents.tryEmit(CallManagerEvent.IncomingCall(offer.callId, callerName, isVideo))
        platform.startIncomingCallService(offer.callId, offer.callerId, callerName, isVideo)
        startRingTimeout(offer.callId)
    }

    override suspend fun onCallLifecycle(event: CallLifecycleSignal) {
        val callId = currentCallId()
        if (callId == null || callId != event.callId) {
            Log.d(TAG, "Ignoring lifecycle ${event.eventType} for ${event.callId} (current: $callId)")
            return
        }
        applyControl(event.eventType, event.callId)
    }

    suspend fun answerCall() {
        val initial = _callState.value
        if (initial !is CallState.Incoming) {
            Log.w(TAG, "Cannot answer: not in incoming state")
            return
        }
        val state = awaitOffer(initial)
        if (state == null) {
            Log.e(TAG, "Answering ${initial.callId}: the offer never arrived")
            endCallOnServer(initial.callId, "failed")
            handleCallEnded(initial.callId, CallState.EndReason.FAILED)
            return
        }
        try {
            platform.configureAudioSession(state.isVideo)
            _isSpeakerOn.value = state.isVideo
            webRTCClient.configure(platform.buildIceServers(callServiceClient.getTurnCredentials(GetTurnCredentialsRequest)))
            webRTCClient.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, state.sdpOffer))
            webRTCClient.startLocalMedia(state.isVideo)
            val answer = webRTCClient.createAnswer()
            webRTCClient.setLocalDescription(answer)

            stream.open(state.callId, CallStream.ROLE_CALLEE)
            val sealed = signalingCrypto.sealAnswer(answer.description, "answer", state.callerId, state.callerDevice)
            stream.send(state.callId, CallSignalPayload.EncryptedSdpAnswer(sealed))
            stream.send(state.callId, CallSignalPayload.Control("accepted"))
            onCallConnected(state.callId, state.callerId, state.callerName, state.isVideo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to answer call", e)
            endCallOnServer(state.callId, "failed")
            handleCallEnded(state.callId, CallState.EndReason.FAILED)
        }
    }

    /** For a call that rang from a push, the SDP may still be in flight on the stream. */
    private suspend fun awaitOffer(initial: CallState.Incoming): CallState.Incoming? {
        if (initial.sdpOffer.isNotEmpty()) return initial
        val deadline = System.currentTimeMillis() + OFFER_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            val now = _callState.value
            if (now !is CallState.Incoming || now.callId != initial.callId) return null
            if (now.sdpOffer.isNotEmpty()) return now
            delay(OFFER_POLL_MS)
        }
        return null
    }

    private fun startRingTimeout(callId: String) {
        timeoutJob?.cancel()
        timeoutJob =
            scope.launch {
                delay(CALL_TIMEOUT_MS)
                val state = _callState.value
                if (state is CallState.Incoming && state.callId == callId) {
                    Log.w(TAG, "Incoming call $callId unanswered")
                    endCallOnServer(callId, "missed")
                    handleCallEnded(callId, CallState.EndReason.TIMEOUT)
                }
            }
    }

    fun declineCall() {
        val state = _callState.value
        if (state !is CallState.Incoming) return
        scope.launch {
            endCallOnServer(state.callId, "declined")
            handleCallEnded(state.callId, CallState.EndReason.DECLINED)
        }
    }

    suspend fun endCall() {
        val state = _callState.value
        val callId = currentCallId() ?: return
        if (state is CallState.Ended) return
        val reason =
            when (state) {
                is CallState.Incoming -> "declined"
                is CallState.Outgoing, is CallState.Ringing -> "cancelled"
                else -> "ended"
            }
        stream.send(callId, CallSignalPayload.Control(reason))
        endCallOnServer(callId, reason)
        handleCallEnded(callId, CallState.EndReason.NORMAL)
    }

    // ------------------------------------------------------------------
    // Controls
    // ------------------------------------------------------------------

    fun toggleMute() {
        val muted = webRTCClient.toggleMute()
        _isMuted.value = muted
        currentCallId()?.let { stream.send(it, CallSignalPayload.Control(if (muted) "muted" else "unmuted")) }
    }

    fun toggleSpeaker() {
        _isSpeakerOn.value = webRTCClient.toggleSpeaker()
    }

    /** In a video call, the camera toggle; in a voice call, a request to upgrade to video (as iOS). */
    fun toggleVideo() {
        if (_callType.value != "video") {
            requestVideoUpgrade()
            return
        }
        val enabled = webRTCClient.toggleVideo()
        _isVideoEnabled.value = enabled
        currentCallId()?.let { stream.send(it, CallSignalPayload.Control(if (enabled) "video_on" else "video_off")) }
    }

    fun switchCamera() {
        webRTCClient.switchCamera()
    }

    fun requestVideoUpgrade() {
        currentCallId()?.let(videoUpgrade::request)
    }

    fun acceptVideoUpgrade() {
        currentCallId()?.let(videoUpgrade::accept)
    }

    fun declineVideoUpgrade() {
        currentCallId()?.let(videoUpgrade::decline)
    }

    // ------------------------------------------------------------------
    // Signaling stream
    // ------------------------------------------------------------------

    private suspend fun handleSignal(
        callId: String,
        signal: CallSignal,
    ) {
        if (signal.callId != callId) return
        when (val payload = signal.payload) {
            is CallSignalPayload.EncryptedSdpAnswer -> handleEncryptedAnswer(callId, payload.ciphertext, signal.peerDevice)
            is CallSignalPayload.IceCandidate -> {
                val candidate = IceCandidateCodec.decode(payload.json) ?: return
                try {
                    webRTCClient.addIceCandidate(candidate)
                } catch (e: WebRTCException) {
                    Log.w(TAG, "Failed to add remote ICE candidate: ${e.message}")
                }
            }
            is CallSignalPayload.Control -> applyControl(payload.action, callId)
            is CallSignalPayload.Join -> Log.d(TAG, "Ignoring join echo for $callId")
        }
    }

    private suspend fun handleEncryptedAnswer(
        callId: String,
        ciphertext: ByteArray,
        fromDevice: Int,
    ) {
        val sender = peerId ?: return
        val payload =
            try {
                signalingCrypto.open(ciphertext, sender, fromDevice, ANSWER_MAX_AGE_SECS)
            } catch (e: CallPayloadException) {
                Log.e(TAG, "Rejecting SDP answer for $callId: ${e.message}")
                return
            }
        peerDevice = CallSignalingCrypto.resolveDevice(fromDevice)
        if (payload.type == "offer") {
            // A mid-call renegotiation: the peer's video upgrade offer.
            videoUpgrade.onRemoteOffer(callId, payload.sdp)
            return
        }
        try {
            webRTCClient.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, payload.sdp))
        } catch (e: WebRTCException) {
            Log.e(TAG, "Failed to apply SDP answer", e)
            return
        }
        videoUpgrade.onRemoteAnswerApplied()
        Log.i(TAG, "E2EE answer verified for $callId (fingerprint ${payload.dtlsFingerprint})")
        val state = _callState.value
        if (state is CallState.Ringing) onCallConnected(callId, state.recipientId, state.recipientName, state.isVideo)
        if (state is CallState.Outgoing) onCallConnected(callId, state.recipientId, state.recipientName, state.isVideo)
    }

    /** Control actions and lifecycle event types share a vocabulary; both land here. */
    private fun applyControl(
        action: String,
        callId: String,
    ) {
        when (action) {
            "ringing" -> {
                val state = _callState.value
                if (state is CallState.Outgoing) {
                    _callState.value =
                        CallState.Ringing(state.callId.ifEmpty { callId }, state.recipientId, state.recipientName, state.isVideo)
                }
            }
            "accepted" -> {
                val state = _callState.value
                if (state is CallState.Ringing) onCallConnected(callId, state.recipientId, state.recipientName, state.isVideo)
            }
            "declined" -> handleCallEnded(callId, CallState.EndReason.DECLINED)
            "busy" -> handleCallEnded(callId, CallState.EndReason.BUSY)
            "ended", "cancelled" -> handleCallEnded(callId, CallState.EndReason.NORMAL)
            "missed" -> handleCallEnded(callId, CallState.EndReason.TIMEOUT)
            "failed" -> handleCallEnded(callId, CallState.EndReason.FAILED)
            "ping", "pong", "muted", "unmuted", "battery_low", "battery_ok" -> Log.d(TAG, "Control '$action' for $callId noted")
            else -> if (!videoUpgrade.onControl(action, callId)) Log.w(TAG, "Unknown control action '$action' for $callId")
        }
    }

    // ------------------------------------------------------------------
    // WebRTC events
    // ------------------------------------------------------------------

    private fun observeWebRTCEvents() {
        webRTCEventsJob =
            scope.launch {
                webRTCClient.events.collect { event ->
                    when (event) {
                        is WebRTCEvent.LocalIceCandidate -> {
                            val json = IceCandidateCodec.encode(event.candidate)
                            val callId = currentCallId()
                            if (callId == null ||
                                callId.isEmpty()
                            ) {
                                stream.bufferIce(json)
                            } else {
                                stream.send(callId, CallSignalPayload.IceCandidate(json))
                            }
                        }
                        is WebRTCEvent.ConnectionStateChanged -> handleConnectionStateChange(event.state)
                        is WebRTCEvent.RemoteVideoTrackReceived -> videoUpgrade.onRemoteTrack()
                        is WebRTCEvent.SignalingStateChanged -> Log.d(TAG, "Signaling state: ${event.state}")
                        is WebRTCEvent.IceGatheringComplete -> Log.d(TAG, "ICE gathering complete")
                        is WebRTCEvent.PeerConnectionError -> Log.e(TAG, "PeerConnection error: ${event.message}")
                    }
                }
            }
    }

    private fun handleConnectionStateChange(state: PeerConnection.IceConnectionState) {
        val current = _callState.value
        val callId = currentCallId() ?: return
        when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED,
            -> {
                if (current is CallState.Reconnecting) {
                    _callState.value =
                        CallState.Active(
                            callId,
                            current.remoteUserId,
                            current.remoteUserName,
                            current.isVideo,
                            System.currentTimeMillis(),
                        )
                }
            }
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                if (current is CallState.Active) {
                    _callState.value = CallState.Reconnecting(callId, current.remoteUserId, current.remoteUserName, current.isVideo)
                }
            }
            PeerConnection.IceConnectionState.FAILED -> handleCallEnded(callId, CallState.EndReason.NETWORK_ERROR)
            else -> Unit
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    private fun onCallConnected(
        callId: String,
        remoteUserId: String,
        remoteUserName: String,
        isVideo: Boolean,
    ) {
        if (_callState.value is CallState.Active) return
        cancelTimeout()
        _callState.value = CallState.Active(callId, remoteUserId, remoteUserName, isVideo, System.currentTimeMillis())
        videoUpgrade.onConnected(isVideo)
        startDurationTimer()
        _callEvents.tryEmit(CallManagerEvent.CallConnected(callId))
    }

    private fun handleCallEnded(
        callId: String,
        reason: CallState.EndReason,
    ) {
        if (_callState.value is CallState.Ended || _callState.value is CallState.Idle) return
        durationJob?.cancel()
        durationJob = null
        stream.close()
        cancelTimeout()
        webRTCClient.close()
        platform.resetAudioSession()
        videoUpgrade.onEnded()
        _callState.value = CallState.Ended(callId = callId, reason = reason)
        _callEvents.tryEmit(CallManagerEvent.CallEnded(callId, reason))
        scope.launch {
            delay(ENDED_LINGER_MS)
            if (_callState.value is CallState.Ended) {
                _callState.value = CallState.Idle
                peerId = null
                peerDevice = 0
                resetCallControls()
            }
        }
    }

    private suspend fun endCallOnServer(
        callId: String,
        reason: String,
    ) {
        if (callId.isEmpty()) return
        try {
            // gRPC coroutine stubs suspend without blocking; no dispatcher hop needed.
            callServiceClient.endCall(EndCallRequest(callId, reason))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "EndCall($reason) failed for $callId: ${e.message}")
        }
    }

    private fun resetCallControls() {
        _isMuted.value = false
        _isSpeakerOn.value = false
        _isVideoEnabled.value = true
        _callDuration.value = 0L
        _callType.value = "voice"
        videoUpgrade.reset()
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
                    endCallOnServer(callId, "missed")
                    handleCallEnded(callId, CallState.EndReason.TIMEOUT)
                }
            }
    }

    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun localDeviceId(): Int = sessionManager.getDeviceId()?.toIntOrNull()?.takeIf { it > 0 } ?: 1

    private fun currentCallId(): String? =
        when (val state = _callState.value) {
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
