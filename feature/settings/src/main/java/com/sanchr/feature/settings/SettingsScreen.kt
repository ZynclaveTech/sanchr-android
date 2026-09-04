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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val accountDeletion by viewModel.accountDeletion.collectAsStateWithLifecycle()
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

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

            // Delete Account. Deliberately the only exit from Sanchr: there
            // is no sign-out, because a signed-out install still holds account
            // material on-device.
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { showDeleteAccountDialog = true }
                        .padding(
                            horizontal = SanchrTheme.spacing.default,
                            vertical = SanchrTheme.spacing.md,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(SanchrTheme.spacing.default))
                Text(
                    text = "Delete Account",
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

    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            state = accountDeletion,
            onConfirm = viewModel::deleteAccount,
            onDismiss = {
                showDeleteAccountDialog = false
                viewModel.dismissAccountDeletionError()
            },
        )
    }
}

/**
 * Confirmation for the one irreversible action in the app. Two deliberate
 * frictions, mirroring iOS's `DeleteAccountConfirmationSheet`: the
 * consequences are named rather than summarised, and the destructive button
 * stays disabled until the user actively acknowledges them — a plain
 * "Are you sure?" is too easy to dismiss for something unrecoverable.
 *
 * The dialog cannot be dismissed while the delete is in flight, so a
 * half-completed deletion cannot be hidden by tapping outside.
 */
@Composable
private fun DeleteAccountDialog(
    state: AccountDeletionState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var acknowledged by remember { mutableStateOf(false) }
    val inProgress = state is AccountDeletionState.InProgress

    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Filled.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(text = "Delete Account") },
        text = {
            Column {
                Text(
                    text = "This permanently deletes your account. It cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                listOf(
                    "Your account and phone number are removed from Sanchr",
                    "Your message history on this device is erased",
                    "You are removed from all your conversations",
                    "Your encryption keys are destroyed and cannot be recovered",
                ).forEach { consequence ->
                    Text(
                        text = "•  $consequence",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state is AccountDeletionState.Failed) {
                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = acknowledged,
                        onCheckedChange = { acknowledged = it },
                        enabled = !inProgress,
                    )
                    Text(
                        text = "I understand this cannot be undone",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = acknowledged && !inProgress,
            ) {
                Text(
                    text = if (inProgress) "Deleting…" else "Delete Account",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) {
                Text(text = "Cancel")
            }
        },
    )
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
