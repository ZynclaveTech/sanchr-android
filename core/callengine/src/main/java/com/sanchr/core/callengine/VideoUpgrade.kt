package com.sanchr.core.callengine

import android.util.Log
import com.sanchr.core.callengine.signaling.CallSignalingCrypto
import com.sanchr.proto.calling.CallSignalPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.SessionDescription

/**
 * The mid-call voice→video upgrade, iOS's handshake: the requester sends
 * `video_request`; the peer replies `video_accept` (or `video_decline`);
 * the requester then puts a sealed renegotiation offer (type "offer") on
 * the stream and the peer answers it with a sealed answer. `video_failed`
 * from either side drops both back to voice. Owned by [CallManager], which
 * routes stream frames and control actions here.
 */
internal class VideoUpgrade(
    private val webRTCClient: WebRTCClient,
    private val platform: CallPlatform,
    private val signalingCrypto: CallSignalingCrypto,
    private val scope: CoroutineScope,
    private val send: (callId: String, payload: CallSignalPayload) -> Unit,
    /** The peer's user id and Signal device for the current call, or null outside a call. */
    private val peer: () -> Pair<String, Int>?,
    private val isActiveCall: (callId: String) -> Boolean,
    private val callType: MutableStateFlow<String>,
    private val isVideoEnabled: MutableStateFlow<Boolean>,
    private val isSpeakerOn: MutableStateFlow<Boolean>,
) {
    /** The peer's camera is on (`video_on` / `video_off`, and true once an upgrade completes). */
    private val _peerVideoEnabled = MutableStateFlow(false)
    val peerVideoEnabled: StateFlow<Boolean> = _peerVideoEnabled.asStateFlow()

    /** A remote video track has been attached; the renderer must (re)attach when this flips. */
    private val _hasRemoteVideoTrack = MutableStateFlow(false)
    val hasRemoteVideoTrack: StateFlow<Boolean> = _hasRemoteVideoTrack.asStateFlow()

    /** The peer asked to switch to video; answer with [accept] or [decline]. */
    private val _incomingRequest = MutableStateFlow(false)
    val incomingRequest: StateFlow<Boolean> = _incomingRequest.asStateFlow()

    /** We asked for video and are waiting for `video_accept` / `video_decline`. */
    private val _outgoingPending = MutableStateFlow(false)
    val outgoingPending: StateFlow<Boolean> = _outgoingPending.asStateFlow()

    fun request(callId: String) {
        if (!isActiveCall(callId)) {
            Log.w(TAG, "Ignoring video request outside an active call")
            return
        }
        if (callType.value == VIDEO) {
            isVideoEnabled.value = webRTCClient.setVideoEnabled(true)
            return
        }
        if (_outgoingPending.value) return
        _outgoingPending.value = true
        send(callId, CallSignalPayload.Control("video_request"))
    }

    fun accept(callId: String) {
        if (!_incomingRequest.value) return
        _incomingRequest.value = false
        switchToVideo()
        _peerVideoEnabled.value = true
        send(callId, CallSignalPayload.Control("video_accept"))
        send(callId, CallSignalPayload.Control("video_on"))
    }

    fun decline(callId: String) {
        if (!_incomingRequest.value) return
        _incomingRequest.value = false
        send(callId, CallSignalPayload.Control("video_decline"))
    }

    /** Applies a `video_*` control action; false when [action] is not one. */
    fun onControl(
        action: String,
        callId: String,
    ): Boolean {
        when (action) {
            "video_request" -> onPeerRequest(callId)
            "video_accept" -> {
                if (_outgoingPending.value) {
                    _peerVideoEnabled.value = true
                    startOffer(callId)
                } else {
                    Log.i(TAG, "Ignoring duplicate video_accept for $callId")
                }
            }
            "video_decline" -> _outgoingPending.value = false
            "video_on" -> _peerVideoEnabled.value = true
            "video_off" -> _peerVideoEnabled.value = false
            "video_failed" -> dropToVoice()
            else -> return false
        }
        return true
    }

    /** The peer's sealed renegotiation offer arrived on the stream. */
    suspend fun onRemoteOffer(
        callId: String,
        sdp: String,
    ) {
        try {
            switchToVideo()
            _peerVideoEnabled.value = true
            webRTCClient.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, sdp))
            val answer = webRTCClient.createAnswer()
            webRTCClient.setLocalDescription(answer)
            sendSealed(callId, answer.description, "answer")
            Log.i(TAG, "Video upgrade answer sent for $callId")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Answering video upgrade failed for $callId", e)
            fail(callId)
        }
    }

    /** The peer answered our renegotiation offer. */
    fun onRemoteAnswerApplied() {
        _outgoingPending.value = false
    }

    fun onRemoteTrack() {
        _hasRemoteVideoTrack.value = true
    }

    fun onConnected(isVideo: Boolean) {
        _peerVideoEnabled.value = isVideo
    }

    /** Dismisses any prompt the moment the call ends. */
    fun onEnded() {
        _incomingRequest.value = false
        _outgoingPending.value = false
    }

    fun reset() {
        onEnded()
        _peerVideoEnabled.value = false
        _hasRemoteVideoTrack.value = false
    }

    private fun onPeerRequest(callId: String) {
        if (!isActiveCall(callId)) {
            Log.i(TAG, "Ignoring video request outside active call $callId")
            return
        }
        if (callType.value == VIDEO) {
            send(callId, CallSignalPayload.Control("video_accept"))
            return
        }
        _incomingRequest.value = true
    }

    private fun startOffer(callId: String) {
        scope.launch {
            try {
                switchToVideo()
                val offer = webRTCClient.createOffer()
                webRTCClient.setLocalDescription(offer)
                sendSealed(callId, offer.description, "offer")
                Log.i(TAG, "Video upgrade offer sent for $callId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Video upgrade failed for $callId", e)
                fail(callId)
            }
        }
    }

    private suspend fun sendSealed(
        callId: String,
        sdp: String,
        type: String,
    ) {
        val (recipient, device) = peer() ?: error("no peer for $callId")
        send(callId, CallSignalPayload.EncryptedSdpAnswer(signalingCrypto.sealAnswer(sdp, type, recipient, device)))
    }

    private fun switchToVideo() {
        callType.value = VIDEO
        platform.configureAudioSession(isVideo = true)
        isSpeakerOn.value = true
        isVideoEnabled.value = webRTCClient.setVideoEnabled(true)
    }

    private fun fail(callId: String) {
        dropToVoice()
        send(callId, CallSignalPayload.Control("video_failed"))
    }

    private fun dropToVoice() {
        reset()
        if (callType.value == VIDEO) {
            callType.value = "voice"
            isVideoEnabled.value = webRTCClient.setVideoEnabled(false)
        }
    }

    private companion object {
        const val TAG = "VideoUpgrade"
        const val VIDEO = "video"
    }
}
