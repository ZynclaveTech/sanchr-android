package com.sanchr.feature.chats.info

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrCard
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * One conversation's own page: what it is, how it is secured, and the
 * settings that apply to it alone.
 *
 * Android had no such screen. Mute, archive and hide lived only in the chat
 * list's long-press menu, verification lived on the contact's profile, and
 * the per-conversation disappearing timer had no way in at all despite the
 * send path already reading it.
 */
@Composable
fun ConversationInfoScreen(
    onNavigateBack: () -> Unit,
    onVerifySafetyNumber: (peerId: String, peerName: String) -> Unit,
    onOpenDisappearing: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onLeft: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationInfoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val peerId =
        uiState.conversation
            ?.participants
            ?.firstOrNull { it.displayName != "You" }
            ?.id

    Scaffold(
        topBar = { SanchrTopBar(title = uiState.title, onNavigateBack = onNavigateBack) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(SanchrTheme.spacing.default),
            verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.default),
        ) {
            uiState.error?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            SectionTitle("Security and privacy")
            SanchrCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    InfoRow(
                        icon = Icons.Filled.Lock,
                        title = "Encryption",
                        subtitle = "Compare security codes with this contact",
                        // Disabled without a peer: a group has no single
                        // identity to compare against.
                        onClick = peerId?.let { { onVerifySafetyNumber(it, uiState.title) } },
                    )
                    InfoRow(
                        icon = Icons.Filled.Timer,
                        title = "Disappearing messages",
                        subtitle = DisappearingDurations.displayName(uiState.disappearingLabel),
                        onClick = onOpenDisappearing,
                    )
                }
            }

            SectionTitle("Chat preferences")
            SanchrCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ToggleRow(
                        icon = Icons.Filled.Notifications,
                        title = "Mute conversation",
                        subtitle = "Stop notifications for this chat",
                        checked = uiState.isMuted,
                        onCheckedChange = viewModel::setMuted,
                    )
                    InfoRow(
                        icon = Icons.Filled.Palette,
                        title = "Wallpaper and theme",
                        subtitle = "Change how this chat looks",
                        onClick = onOpenWallpaper,
                    )
                }
            }

            SectionTitle("This device")
            SanchrCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    InfoRow(
                        icon = if (uiState.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                        title = if (uiState.isArchived) "Unarchive chat" else "Archive chat",
                        subtitle = if (uiState.isArchived) "Move it back to the chat list" else "Move it out of the chat list",
                        onClick = { viewModel.setArchived(!uiState.isArchived) },
                    )
                    InfoRow(
                        icon = Icons.Filled.VisibilityOff,
                        title = "Hide from this device",
                        subtitle = "Nothing is deleted; restore it from Hidden",
                        onClick = {
                            viewModel.hide()
                            onLeft()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** A tappable row; a null [onClick] renders it as unavailable rather than doing nothing when pressed. */
@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
) {
    val enabled = onClick != null
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(SanchrTheme.spacing.default),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.md),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.default),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.md),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
