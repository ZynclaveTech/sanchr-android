package com.sanchr.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrTheme

@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SanchrTopBar(
                title = "Notifications",
                onNavigateBack = onNavigateBack,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(SanchrTheme.spacing.default),
        ) {
            // -- Master toggle --
            SettingsToggleRow(
                title = "Enable Notifications",
                subtitle = "Receive push notifications for new messages and calls",
                checked = uiState.notificationsEnabled,
                onCheckedChange = viewModel::setNotificationsEnabled,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // -- Message Notifications --
            Text(
                text = "Message Notifications",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SettingsToggleRow(
                title = "Direct Messages",
                subtitle = "Notifications for one-on-one conversations",
                checked = uiState.messageNotificationsEnabled,
                onCheckedChange = viewModel::setMessageNotificationsEnabled,
            )

            SettingsToggleRow(
                title = "Group Messages",
                subtitle = "Notifications for group conversations",
                checked = uiState.groupNotificationsEnabled,
                onCheckedChange = viewModel::setGroupNotificationsEnabled,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // -- Call Notifications --
            Text(
                text = "Call Notifications",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SettingsToggleRow(
                title = "Incoming Calls",
                subtitle = "Show notification for incoming voice and video calls",
                checked = uiState.callNotificationsEnabled,
                onCheckedChange = viewModel::setCallNotificationsEnabled,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // -- Sound & Vibration --
            Text(
                text = "Sound & Vibration",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SettingsToggleRow(
                title = "Sound",
                subtitle = "Play a sound when a notification arrives",
                checked = uiState.notificationSoundEnabled,
                onCheckedChange = viewModel::setNotificationSoundEnabled,
            )

            SettingsToggleRow(
                title = "Vibration",
                subtitle = "Vibrate when a notification arrives",
                checked = uiState.notificationVibrationEnabled,
                onCheckedChange = viewModel::setNotificationVibrationEnabled,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // -- Notification Preview --
            Text(
                text = "Notification Preview",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            Text(
                text = "Controls what is shown in notification previews",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            NotificationPreviewOption(
                label = "Always",
                description = "Show sender name and message content",
                selected = uiState.notificationPreview == "always",
                onClick = { viewModel.setNotificationPreview("always") },
            )

            NotificationPreviewOption(
                label = "Contacts only",
                description = "Only show preview for known contacts",
                selected = uiState.notificationPreview == "contacts",
                onClick = { viewModel.setNotificationPreview("contacts") },
            )

            NotificationPreviewOption(
                label = "Never",
                description = "Never show sender name or message content",
                selected = uiState.notificationPreview == "never",
                onClick = { viewModel.setNotificationPreview("never") },
            )
        }
    }
}

@Composable
private fun NotificationPreviewOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = SanchrTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Column(modifier = Modifier.weight(1f).padding(start = SanchrTheme.spacing.sm)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SanchrTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
