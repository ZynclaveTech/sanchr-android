package com.sanchr.feature.calls

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrError
import com.sanchr.core.designsystem.theme.SanchrSuccess
import com.sanchr.core.designsystem.theme.SanchrTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsListScreen(
    onCallClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SanchrTopBar(title = "Calls")
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            // Filter tabs
            LazyRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = SanchrTheme.spacing.default,
                            vertical = SanchrTheme.spacing.sm,
                        ),
                horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
            ) {
                items(CallFilter.entries.toList()) { filter ->
                    FilterChip(
                        selected = filter == uiState.filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                uiState.entries.isEmpty() -> {
                    EmptyCallsState()
                }

                else -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = uiState.entries,
                                key = { it.entry.callId },
                            ) { item ->
                                CallEntryRow(
                                    item = item,
                                    onClick = { onCallClick(item.entry.peerId) },
                                    onCallBack = {
                                        if (item.isVideo) {
                                            viewModel.startVideoCall(item.entry.peerId, item.displayName)
                                        } else {
                                            viewModel.startVoiceCall(item.entry.peerId, item.displayName)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCallsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Call,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.default))
            Text(
                text = "No call history",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
            Text(
                text = "Your calls will appear here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CallEntryRow(
    item: CallLogItem,
    onClick: () -> Unit,
    onCallBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = item.entry
    val isMissed = item.isMissed
    val isOutgoing = item.isOutgoing
    val isVideo = item.isVideo
    val displayName = item.displayName

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    horizontal = SanchrTheme.spacing.default,
                    vertical = SanchrTheme.spacing.md,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = displayName.take(1).uppercase().ifEmpty { "?" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.width(SanchrTheme.spacing.md))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = displayName.ifEmpty { "Unknown" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isMissed) SanchrError else MaterialTheme.colorScheme.onSurface,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Direction icon
                val (dirIcon, dirColor) =
                    when {
                        isMissed -> Icons.Filled.CallMissed to SanchrError
                        isOutgoing -> Icons.Filled.CallMade to SanchrSuccess
                        else -> Icons.Filled.CallReceived to SanchrSuccess
                    }

                Icon(
                    imageVector = dirIcon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = dirColor,
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Call type icon
                Icon(
                    imageVector = if (isVideo) Icons.Filled.Videocam else Icons.Filled.Call,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.width(SanchrTheme.spacing.xs))

                // Timestamp
                Text(
                    text = formatTimestamp(entry.startedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Duration for completed calls
                if (!isMissed && entry.durationSecs > 0) {
                    Text(
                        text = " - ${formatDuration(entry.durationSecs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Call-back button
        IconButton(onClick = onCallBack) {
            Icon(
                imageVector = if (isVideo) Icons.Filled.Videocam else Icons.Filled.Call,
                contentDescription = if (isVideo) "Video call" else "Voice call",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis == 0L) return ""
    return try {
        val now = System.currentTimeMillis()
        val diff = now - epochMillis
        val oneDay = 24 * 60 * 60 * 1000L

        when {
            diff < oneDay -> {
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))
            }
            diff < 7 * oneDay -> {
                SimpleDateFormat("EEE", Locale.getDefault()).format(Date(epochMillis))
            }
            else -> {
                SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMillis))
            }
        }
    } catch (_: Exception) {
        ""
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes > 0) "${minutes}m ${secs}s" else "${secs}s"
}
