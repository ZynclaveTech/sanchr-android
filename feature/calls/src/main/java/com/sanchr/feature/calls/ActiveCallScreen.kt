package com.sanchr.feature.calls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.callengine.CallState
import com.sanchr.core.designsystem.theme.SanchrError
import com.sanchr.core.designsystem.theme.SanchrGradients
import com.sanchr.core.designsystem.theme.SanchrSuccess
import com.sanchr.core.designsystem.theme.SanchrTheme
import org.webrtc.SurfaceViewRenderer
import kotlin.math.roundToInt

@Composable
fun ActiveCallScreen(
    callId: String,
    onCallEnded: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallsViewModel = hiltViewModel(),
) {
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsStateWithLifecycle()
    val isVideoEnabled by viewModel.isVideoEnabled.collectAsStateWithLifecycle()
    val callDuration by viewModel.callDuration.collectAsStateWithLifecycle()
    val callType by viewModel.callType.collectAsStateWithLifecycle()

    val isVideoCall = callType == "video"
    val isIncoming = callState is CallState.Incoming
    val isActive = callState is CallState.Active
    val isRinging = callState is CallState.Ringing ||
        callState is CallState.Outgoing

    // Navigate away when call ends and returns to idle
    LaunchedEffect(callState) {
        if (callState is CallState.Idle) {
            onCallEnded()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SanchrGradients.CallActive),
    ) {
        // Video mode: remote video fills the screen
        if (isVideoCall && isActive) {
            RemoteVideoView(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Main content overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(SanchrTheme.spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.huge))

            // Caller info (hide during active video call to show video)
            if (!isVideoCall || !isActive) {
                CallerInfoSection(
                    callState = callState,
                    callDuration = callDuration,
                    isRinging = isRinging,
                )
            }

            if (isVideoCall && isActive) {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Call controls
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isIncoming) {
                    IncomingCallControls(
                        onAnswer = viewModel::answerCall,
                        onDecline = viewModel::declineCall,
                    )
                } else {
                    ActiveCallControls(
                        isMuted = isMuted,
                        isSpeakerOn = isSpeakerOn,
                        isVideoEnabled = isVideoEnabled,
                        isVideoCall = isVideoCall,
                        onToggleMute = viewModel::toggleMute,
                        onToggleSpeaker = viewModel::toggleSpeaker,
                        onToggleVideo = viewModel::toggleVideo,
                        onSwitchCamera = viewModel::switchCamera,
                        onEndCall = {
                            viewModel.endCall()
                        },
                    )
                }

                Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))
            }
        }

        // Local video PiP (top-right, draggable) during active video call
        if (isVideoCall && isActive && isVideoEnabled) {
            LocalVideoPiP(
                viewModel = viewModel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = SanchrTheme.spacing.default),
            )
        }

        // Encryption badge at top center
        if (isActive || isRinging) {
            EncryptionBadge(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = SanchrTheme.spacing.xxxl),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Caller info section
// ---------------------------------------------------------------------------

@Composable
private fun CallerInfoSection(
    callState: CallState,
    callDuration: Long,
    isRinging: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Pulsing avatar
        val callerName = extractCallerName(callState)
        val initial = callerName.firstOrNull()?.uppercase() ?: "?"

        Box(contentAlignment = Alignment.Center) {
            // Pulse rings for ringing state
            if (isRinging) {
                PulseRing(delayMillis = 0)
                PulseRing(delayMillis = 600)
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                modifier = Modifier.size(120.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

        Text(
            text = callerName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
        )

        Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

        val statusText = when (callState) {
            is CallState.Outgoing -> "Calling..."
            is CallState.Ringing -> "Ringing..."
            is CallState.Incoming -> {
                val type = if (callState.isVideo) "video" else "voice"
                "Incoming $type call"
            }
            is CallState.Active -> formatDuration(callDuration)
            is CallState.Reconnecting -> "Reconnecting..."
            is CallState.Ended -> {
                when (callState.reason) {
                    CallState.EndReason.NORMAL -> "Call ended"
                    CallState.EndReason.BUSY -> "Busy"
                    CallState.EndReason.DECLINED -> "Declined"
                    CallState.EndReason.FAILED -> "Call failed"
                    CallState.EndReason.TIMEOUT -> "No answer"
                    CallState.EndReason.NETWORK_ERROR -> "Connection lost"
                }
            }
            is CallState.Idle -> ""
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun PulseRing(delayMillis: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                delayMillis = delayMillis,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseScale",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                delayMillis = delayMillis,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(scale)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = alpha),
                shape = CircleShape,
            ),
    )
}

// ---------------------------------------------------------------------------
// Call controls
// ---------------------------------------------------------------------------

@Composable
private fun ActiveCallControls(
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isVideoEnabled: Boolean,
    isVideoCall: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Mute
                CallControlButton(
                    icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    isActive = isMuted,
                    onClick = onToggleMute,
                )

                // Speaker
                CallControlButton(
                    icon = if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    label = "Speaker",
                    isActive = isSpeakerOn,
                    onClick = onToggleSpeaker,
                )

                // Video toggle
                if (isVideoCall) {
                    CallControlButton(
                        icon = if (isVideoEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                        label = "Video",
                        isActive = !isVideoEnabled,
                        onClick = onToggleVideo,
                    )

                    // Flip camera
                    CallControlButton(
                        icon = Icons.Filled.FlipCameraAndroid,
                        label = "Flip",
                        isActive = false,
                        onClick = onSwitchCamera,
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            // End call FAB
            FloatingActionButton(
                onClick = onEndCall,
                containerColor = SanchrError,
                contentColor = Color.White,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = "End call",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun IncomingCallControls(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Decline
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FloatingActionButton(
                onClick = onDecline,
                containerColor = SanchrError,
                contentColor = Color.White,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = "Decline",
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
            Text(
                text = "Decline",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
        }

        // Answer
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FloatingActionButton(
                onClick = onAnswer,
                containerColor = SanchrSuccess,
                contentColor = Color.White,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = "Answer",
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
            Text(
                text = "Answer",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = if (isActive) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f)
            },
            modifier = Modifier.size(56.dp),
        ) {
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
        )
    }
}

// ---------------------------------------------------------------------------
// Video views
// ---------------------------------------------------------------------------

@Composable
private fun RemoteVideoView(
    viewModel: CallsViewModel,
    modifier: Modifier = Modifier,
) {
    val webRTCClient = viewModel.webRTCClient

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                webRTCClient.initSurfaceViewRenderer(this)
                webRTCClient.attachRemoteRenderer(this)
                setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            }
        },
        modifier = modifier,
        onRelease = { renderer ->
            webRTCClient.detachRemoteRenderer(renderer)
            renderer.release()
        },
    )
}

@Composable
private fun LocalVideoPiP(
    viewModel: CallsViewModel,
    modifier: Modifier = Modifier,
) {
    val webRTCClient = viewModel.webRTCClient

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .size(width = 120.dp, height = 160.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceViewRenderer(ctx).apply {
                    webRTCClient.initSurfaceViewRenderer(this)
                    webRTCClient.attachLocalRenderer(this)
                    setMirror(true)
                    setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { renderer ->
                webRTCClient.detachLocalRenderer(renderer)
                renderer.release()
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Encryption badge
// ---------------------------------------------------------------------------

@Composable
private fun EncryptionBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.1f),
        modifier = modifier,
    ) {
        Text(
            text = "End-to-end encrypted",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 4.dp,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun extractCallerName(state: CallState): String = when (state) {
    is CallState.Outgoing -> state.recipientName
    is CallState.Incoming -> state.callerName
    is CallState.Ringing -> state.recipientName
    is CallState.Active -> state.remoteUserName
    is CallState.Reconnecting -> state.remoteUserName
    is CallState.Ended -> ""
    is CallState.Idle -> ""
}

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
