package com.sanchr.feature.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sanchr.core.designsystem.component.SanchrCard
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
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SanchrTopBar(title = "Settings")
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
        ) {
            // Profile header card
            SanchrCard(
                onClick = onNavigateToProfile,
                modifier =
                    Modifier.padding(
                        horizontal = SanchrTheme.spacing.default,
                        vertical = SanchrTheme.spacing.sm,
                    ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(SanchrTheme.spacing.default),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uiState.avatarUrl.isNotEmpty()) {
                        AsyncImage(
                            model = uiState.avatarUrl,
                            contentDescription = "Profile avatar",
                            modifier =
                                Modifier
                                    .size(56.dp)
                                    .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text =
                                        uiState.displayName
                                            .take(1)
                                            .uppercase()
                                            .ifEmpty { "?" },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(SanchrTheme.spacing.default))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.displayName.ifEmpty { "Set up your profile" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (uiState.bio.isNotEmpty()) {
                            Text(
                                text = uiState.bio,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        if (uiState.phoneNumber.isNotEmpty()) {
                            Text(
                                text = uiState.phoneNumber,
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

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            // General group
            SettingsGroupLabel(text = "General")

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
                icon = Icons.AutoMirrored.Filled.Chat,
                title = "Chat Settings",
                subtitle = "Bubble style, media, enter to send",
                onClick = onNavigateToChatSettings,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            // Privacy & Security group
            SettingsGroupLabel(text = "Privacy & Security")

            SettingsItem(
                icon = Icons.Filled.VisibilityOff,
                title = "Privacy",
                subtitle = "Read receipts, online status, typing",
                onClick = onNavigateToPrivacy,
            )

            SettingsItem(
                icon = Icons.Filled.Security,
                title = "Security",
                subtitle = "App lock, biometrics, Sanchr Mode",
                onClick = onNavigateToSecurity,
            )

            SettingsItem(
                icon = Icons.Filled.VpnKey,
                title = "Encryption Keys",
                subtitle = "Identity fingerprint, safety numbers",
                onClick = onNavigateToEncryptionKeys,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            // Data group
            SettingsGroupLabel(text = "Data")

            SettingsItem(
                icon = Icons.Filled.Storage,
                title = "Storage & Data",
                subtitle = "Storage usage, auto-download, cache",
                onClick = onNavigateToStorage,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.sm))

            SettingsItem(
                icon = Icons.AutoMirrored.Filled.HelpCenter,
                title = "Help Center",
                subtitle = "FAQ, contact support, documentation",
                onClick = onNavigateToHelp,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.sm))

            // Logout button
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = viewModel::logout)
                        .padding(
                            horizontal = SanchrTheme.spacing.default,
                            vertical = SanchrTheme.spacing.md,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(SanchrTheme.spacing.default))
                Text(
                    text = "Log Out",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

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
private fun SettingsGroupLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier.padding(
                horizontal = SanchrTheme.spacing.default,
                vertical = SanchrTheme.spacing.sm,
            ),
    )
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
