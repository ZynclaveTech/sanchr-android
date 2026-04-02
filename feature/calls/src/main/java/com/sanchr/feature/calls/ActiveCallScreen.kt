package com.sanchr.feature.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.callengine.CallState
import com.sanchr.core.designsystem.theme.SanchrError
import com.sanchr.core.designsystem.theme.SanchrGradients
import com.sanchr.core.designsystem.theme.SanchrTheme

@Composable
fun ActiveCallScreen(
    callId: String,
    onCallEnded: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallsViewModel = hiltViewModel(),
) {
    val callState by viewModel.callState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SanchrGradients.CallActive),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(SanchrTheme.spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.huge))

            // Caller info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.size(120.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "?", // TODO: Show caller initial
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

                Text(
                    text = "Unknown", // TODO: Show caller name from call state
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )

                Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

                val statusText = when (callState) {
                    is CallState.Outgoing -> "Calling..."
                    is CallState.Incoming -> "Incoming call"
                    is CallState.Active -> {
                        val duration = (callState as CallState.Active).durationSeconds
                        val minutes = duration / 60
                        val seconds = duration % 60
                        "%d:%02d".format(minutes, seconds)
                    }
                    else -> "Connecting..."
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                )

                Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

                Text(
                    text = "End-to-end encrypted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                )
            }

            // Call controls
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val isMuted = (callState as? CallState.Active)?.isMuted ?: false
                    val isSpeakerOn = (callState as? CallState.Active)?.isSpeakerOn ?: false

                    // Mute button
                    CallControlButton(
                        icon = {
                            Icon(
                                imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        },
                        label = if (isMuted) "Unmute" else "Mute",
                        onClick = viewModel::toggleMute,
                        isActive = isMuted,
                    )

                    // Speaker button
                    CallControlButton(
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = "Speaker",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        },
                        label = "Speaker",
                        onClick = viewModel::toggleSpeaker,
                        isActive = isSpeakerOn,
                    )
                }

                Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

                // End call button
                FloatingActionButton(
                    onClick = {
                        viewModel.endCall()
                        onCallEnded()
                    },
                    containerColor = SanchrError,
                    contentColor = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        contentDescription = "End call",
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    isActive: Boolean,
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
                icon()
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
