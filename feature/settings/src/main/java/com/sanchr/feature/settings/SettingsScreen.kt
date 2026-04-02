package com.sanchr.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrTheme

@Composable
fun SettingsScreen(
    onNavigateToAppearance: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToChatSettings: () -> Unit,
    onNavigateToEncryptionKeys: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            SanchrTopBar(title = "Settings")
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Profile header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToProfile)
                    .padding(SanchrTheme.spacing.default),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp),
                ) {
                    // TODO: Show user avatar
                }

                Spacer(modifier = Modifier.width(SanchrTheme.spacing.default))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your Name", // TODO: Load from session
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "+1 234 567 8901", // TODO: Load from session
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            SettingsItem(
                icon = Icons.Filled.ColorLens,
                title = "Appearance",
                subtitle = "Theme, colors, font size",
                onClick = onNavigateToAppearance,
            )

            SettingsItem(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                subtitle = "Sounds, previews, alerts",
                onClick = onNavigateToNotifications,
            )

            SettingsItem(
                icon = Icons.Filled.VisibilityOff,
                title = "Privacy",
                subtitle = "Last seen, read receipts, blocked",
                onClick = onNavigateToPrivacy,
            )

            SettingsItem(
                icon = Icons.Filled.Security,
                title = "Security",
                subtitle = "App lock, biometrics",
                onClick = onNavigateToSecurity,
            )

            SettingsItem(
                icon = Icons.Filled.Storage,
                title = "Storage & Data",
                subtitle = "Media auto-download, storage usage",
                onClick = onNavigateToStorage,
            )

            SettingsItem(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = "Chat Settings",
                subtitle = "Wallpaper, font, bubbles",
                onClick = onNavigateToChatSettings,
            )

            SettingsItem(
                icon = Icons.Filled.VpnKey,
                title = "Encryption Keys",
                subtitle = "View and verify safety numbers",
                onClick = onNavigateToEncryptionKeys,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.sm))

            SettingsItem(
                icon = Icons.AutoMirrored.Filled.HelpCenter,
                title = "Help Center",
                subtitle = "FAQ, contact support",
                onClick = onNavigateToHelp,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            Text(
                text = "Sanchr v1.0.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.default))
        }
    }
}

@Composable
internal fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = SanchrTheme.spacing.default,
                vertical = SanchrTheme.spacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(SanchrTheme.spacing.default))

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

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
