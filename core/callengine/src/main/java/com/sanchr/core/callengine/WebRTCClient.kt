package com.sanchr.core.callengine

import android.content.Context
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class WebRTCEvent {
    data class LocalIceCandidate(val candidate: IceCandidate) : WebRTCEvent()
    data class ConnectionStateChanged(val state: PeerConnection.IceConnectionState) : WebRTCEvent()
    data class RemoteVideoTrackReceived(val track: VideoTrack) : WebRTCEvent()
    data class SignalingStateChanged(val state: PeerConnection.SignalingState) : WebRTCEvent()
    data object IceGatheringComplete : WebRTCEvent()
    data class PeerConnectionError(val message: String) : WebRTCEvent()
}

@Singleton
class WebRTCClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _events = MutableSharedFlow<WebRTCEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WebRTCEvent> = _events.asSharedFlow()

    private val eglBase: EglBase = EglBase.create()
    private val factory: PeerConnectionFactory

    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private var videoSource: VideoSource? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var isMuted: Boolean = false
    private var isSpeakerOn: Boolean = false
    private var isVideoEnabled: Boolean = true
    private var isUsingFrontCamera: Boolean = true

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        private const val LOCAL_VIDEO_TRACK_ID = "sanchr_local_video"
        private const val LOCAL_AUDIO_TRACK_ID = "sanchr_local_audio"
        private const val LOCAL_STREAM_ID = "sanchr_local_stream"
        private const val VIDEO_WIDTH = 1280
        private const val VIDEO_HEIGHT = 720
        private const val VIDEO_FPS = 30
    }

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        factory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            )
            .setOptions(PeerConnectionFactory.Options().apply {
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()
    }

    fun configure(iceServers: List<PeerConnection.IceServer>) {
        disposePeerConnection()

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy =
                PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            keyType = PeerConnection.KeyType.ECDSA
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {
                _events.tryEmit(WebRTCEvent.SignalingStateChanged(state))
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                _events.tryEmit(WebRTCEvent.ConnectionStateChanged(state))
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    _events.tryEmit(WebRTCEvent.IceGatheringComplete)
                }
            }

            override fun onIceCandidate(candidate: IceCandidate) {
                _events.tryEmit(WebRTCEvent.LocalIceCandidate(candidate))
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}

            override fun onAddStream(stream: MediaStream) {}

            override fun onRemoveStream(stream: MediaStream) {}

            override fun onDataChannel(channel: DataChannel) {}

            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                val track = receiver.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    _events.tryEmit(WebRTCEvent.RemoteVideoTrackReceived(track))
                }
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    _events.tryEmit(WebRTCEvent.RemoteVideoTrackReceived(track))
                }
            }
        }

        peerConnection = factory.createPeerConnection(rtcConfig, observer)
    }

    fun startLocalMedia(isVideo: Boolean) {
        // Audio
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }

        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack(LOCAL_AUDIO_TRACK_ID, audioSource).apply {
            setEnabled(true)
        }
        peerConnection?.addTrack(localAudioTrack, listOf(LOCAL_STREAM_ID))

        // Video
        if (isVideo) {
            startVideoCapture()
        }
    }

    private fun startVideoCapture() {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        val frontCamera = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        val backCamera = deviceNames.firstOrNull { enumerator.isBackFacing(it) }
        val cameraName = if (isUsingFrontCamera) frontCamera else backCamera
            ?: frontCamera ?: return

        videoCapturer = enumerator.createCapturer(cameraName, null) ?: return

        surfaceTextureHelper = SurfaceTextureHelper.create(
            "SanchrCaptureThread",
            eglBase.eglBaseContext,
        )

        videoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
        videoCapturer!!.initialize(surfaceTextureHelper, context, videoSource!!.capturerObserver)
        videoCapturer!!.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)

        localVideoTrack = factory.createVideoTrack(LOCAL_VIDEO_TRACK_ID, videoSource).apply {
            setEnabled(true)
        }

        peerConnection?.addTrack(localVideoTrack, listOf(LOCAL_STREAM_ID))
        isVideoEnabled = true
    }

    fun stopLocalMedia() {
        localAudioTrack?.setEnabled(false)
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null

        stopVideoCapture()
    }

    private fun stopVideoCapture() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null

        localVideoTrack?.setEnabled(false)
        localVideoTrack?.dispose()
        localVideoTrack = null

        videoSource?.dispose()
        videoSource = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        localAudioTrack?.setEnabled(!isMuted)
        return isMuted
    }

    fun toggleSpeaker(): Boolean {
        isSpeakerOn = !isSpeakerOn
        audioManager.isSpeakerphoneOn = isSpeakerOn
        return isSpeakerOn
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
        isUsingFrontCamera = !isUsingFrontCamera
    }

    fun toggleVideo(): Boolean {
        isVideoEnabled = !isVideoEnabled
        localVideoTrack?.setEnabled(isVideoEnabled)

        if (!isVideoEnabled) {
            videoCapturer?.stopCapture()
        } else {
            videoCapturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
        }
        return isVideoEnabled
    }

    // --- Signaling ---

    suspend fun createOffer(): SessionDescription {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        return suspendCancellableCoroutine { cont ->
            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    cont.resume(sdp)
                }

                override fun onCreateFailure(error: String) {
                    cont.resumeWithException(
                        WebRTCException("Failed to create offer: $error")
                    )
                }

                override fun onSetSuccess() {}
                override fun onSetFailure(error: String) {}
            }, constraints) ?: cont.resumeWithException(
                WebRTCException("PeerConnection is null")
            )
        }
    }

    suspend fun createAnswer(): SessionDescription {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        return suspendCancellableCoroutine { cont ->
            peerConnection?.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    cont.resume(sdp)
                }

                override fun onCreateFailure(error: String) {
                    cont.resumeWithException(
                        WebRTCException("Failed to create answer: $error")
                    )
                }

                override fun onSetSuccess() {}
                override fun onSetFailure(error: String) {}
            }, constraints) ?: cont.resumeWithException(
                WebRTCException("PeerConnection is null")
            )
        }
    }

    suspend fun setLocalDescription(sdp: SessionDescription) {
        suspendCancellableCoroutine { cont ->
            peerConnection?.setLocalDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    cont.resume(Unit)
                }

                override fun onSetFailure(error: String) {
                    cont.resumeWithException(
                        WebRTCException("Failed to set local description: $error")
                    )
                }

                override fun onCreateSuccess(sdp: SessionDescription) {}
                override fun onCreateFailure(error: String) {}
            }, sdp) ?: cont.resumeWithException(
                WebRTCException("PeerConnection is null")
            )
        }
    }

    suspend fun setRemoteDescription(sdp: SessionDescription) {
        suspendCancellableCoroutine { cont ->
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    cont.resume(Unit)
                }

                override fun onSetFailure(error: String) {
                    cont.resumeWithException(
                        WebRTCException("Failed to set remote description: $error")
                    )
                }

                override fun onCreateSuccess(sdp: SessionDescription) {}
                override fun onCreateFailure(error: String) {}
            }, sdp) ?: cont.resumeWithException(
                WebRTCException("PeerConnection is null")
            )
        }
    }

    suspend fun addIceCandidate(candidate: IceCandidate) {
        val pc = peerConnection ?: throw WebRTCException("PeerConnection is null")
        val added = pc.addIceCandidate(candidate)
        if (!added) {
            throw WebRTCException("Failed to add ICE candidate")
        }
    }

    // --- Video Rendering ---

    fun initSurfaceViewRenderer(renderer: SurfaceViewRenderer) {
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setEnableHardwareScaler(true)
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        localVideoTrack?.addSink(renderer)
    }

    fun detachLocalRenderer(renderer: SurfaceViewRenderer) {
        localVideoTrack?.removeSink(renderer)
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        remoteVideoTrack?.addSink(renderer)
    }

    fun detachRemoteRenderer(renderer: SurfaceViewRenderer) {
        remoteVideoTrack?.removeSink(renderer)
    }

    fun getEglBaseContext(): EglBase.Context = eglBase.eglBaseContext

    // --- Cleanup ---

    private fun disposePeerConnection() {
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
    }

    fun close() {
        stopLocalMedia()
        disposePeerConnection()

        remoteVideoTrack = null
        isMuted = false
        isSpeakerOn = false
        isVideoEnabled = true
        isUsingFrontCamera = true

        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    fun release() {
        close()
        factory.dispose()
        eglBase.release()
    }
}

class WebRTCException(message: String) : Exception(message)
