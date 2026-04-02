package com.sanchr.core.callengine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the high-level call state machine.
 * Coordinates between WebRTC, signaling (gRPC), and the UI layer.
 */
@Singleton
class CallManager @Inject constructor(
    private val webRTCClient: WebRTCClient,
) {
    private val _callState = MutableStateFlow(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    /**
     * Initiates an outgoing call to the specified user.
     *
     * @param userId The remote user's ID.
     * @param isVideo Whether to start with video enabled.
     */
    suspend fun startCall(userId: String, isVideo: Boolean) {
        _callState.value = CallState.Outgoing(
            remoteUserId = userId,
            isVideo = isVideo,
        )

        // TODO: Fetch ICE servers from signaling server
        // TODO: Create peer connection
        // TODO: Create SDP offer
        // TODO: Send offer via gRPC signaling
    }

    /**
     * Accepts an incoming call.
     *
     * @param callId The call ID from the incoming notification.
     * @param remoteSdp The SDP offer from the caller.
     */
    suspend fun acceptCall(callId: String, remoteSdp: String) {
        val currentState = _callState.value
        if (currentState !is CallState.Incoming) return

        _callState.value = CallState.Active(
            callId = callId,
            remoteUserId = currentState.remoteUserId,
            isVideo = currentState.isVideo,
            isMuted = false,
            isSpeakerOn = false,
            durationSeconds = 0,
        )

        // TODO: Create peer connection and generate answer
        // TODO: Send answer via gRPC signaling
    }

    /**
     * Rejects or ends the current call.
     */
    suspend fun endCall() {
        val currentState = _callState.value
        if (currentState is CallState.Idle) return

        webRTCClient.release()
        // TODO: Send hangup signal via gRPC

        _callState.value = CallState.Idle
    }

    /**
     * Handles a received incoming call notification.
     */
    fun onIncomingCall(callId: String, callerId: String, callerName: String, isVideo: Boolean) {
        _callState.value = CallState.Incoming(
            callId = callId,
            remoteUserId = callerId,
            remoteUserName = callerName,
            isVideo = isVideo,
        )
    }

    fun toggleMute() {
        val state = _callState.value
        if (state is CallState.Active) {
            val newMuted = !state.isMuted
            webRTCClient.setAudioEnabled(!newMuted)
            _callState.update { state.copy(isMuted = newMuted) }
        }
    }

    fun toggleSpeaker() {
        val state = _callState.value
        if (state is CallState.Active) {
            val newSpeaker = !state.isSpeakerOn
            webRTCClient.setSpeakerEnabled(newSpeaker)
            _callState.update { state.copy(isSpeakerOn = newSpeaker) }
        }
    }
}

sealed interface CallState {
    data object Idle : CallState

    data class Incoming(
        val callId: String,
        val remoteUserId: String,
        val remoteUserName: String,
        val isVideo: Boolean,
    ) : CallState

    data class Outgoing(
        val remoteUserId: String,
        val isVideo: Boolean,
    ) : CallState

    data class Active(
        val callId: String,
        val remoteUserId: String,
        val isVideo: Boolean,
        val isMuted: Boolean,
        val isSpeakerOn: Boolean,
        val durationSeconds: Long,
    ) : CallState
}
