package com.sanchr.feature.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.component.SecureScreen
import com.sanchr.core.designsystem.theme.SanchrTheme

@Composable
fun ChatSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val revealedRecoveryKey = remember { mutableStateOf<String?>(null) }
    val showRestoreDialog = remember { mutableStateOf(false) }
    val restoreRecoveryKey = remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is SettingsEvent.RecoveryKeyRevealed) {
                revealedRecoveryKey.value = event.key
            }
        }
    }

    Scaffold(
        topBar = {
            SanchrTopBar(
                title = "Chat Settings",
                onNavigateBack = onNavigateBack,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(SanchrTheme.spacing.default),
        ) {
            // Bubble Style selector
            Text(
                text = "Bubble Style",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.md),
            ) {
                val styles =
                    listOf(
                        "default" to "Default",
                        "rounded" to "Rounded",
                        "flat" to "Flat",
                    )
                styles.forEach { (value, label) ->
                    val isSelected = uiState.bubbleStyle == value
                    val borderColor =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                                .clickable { viewModel.setBubbleStyle(value) }
                                .padding(SanchrTheme.spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.ChatBubble else Icons.Filled.ChatBubbleOutline,
                            contentDescription = label,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.xl))

            // Enter sends message
            SettingsToggleRow(
                title = "Enter Sends Message",
                subtitle = "Press Enter to send a message instead of adding a new line",
                checked = uiState.enterSendsMessage,
                onCheckedChange = viewModel::setEnterSendsMessage,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            // Media auto-save
            SettingsToggleRow(
                title = "Media Auto-Save",
                subtitle = "Automatically save received photos and videos to your device gallery",
                checked = uiState.mediaAutoSave,
                onCheckedChange = viewModel::setMediaAutoSave,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.xl))

            SettingsToggleRow(
                title = "Chat Backup",
                subtitle = "Encrypted remote backups protected by your recovery key",
                checked = uiState.backupEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        viewModel.beginBackupEnable()
                    } else {
                        viewModel.disableBackup()
                    }
                },
            )

            if (uiState.backupEnabled) {
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                Text(
                    text =
                        "Last backup: " + (
                            uiState.lastBackupAtMillis?.let {
                                java.text.DateFormat
                                    .getDateTimeInstance()
                                    .format(java.util.Date(it))
                            } ?: "Never"
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm)) {
                    Button(
                        onClick = viewModel::backupNow,
                        enabled = !uiState.isBackupBusy,
                    ) {
                        Text("Back Up Now")
                    }
                    Button(onClick = viewModel::revealRecoveryKey) {
                        Text("Reveal recovery key")
                    }
                    TextButton(onClick = viewModel::rotateRecoveryKey) {
                        Text("Rotate key")
                    }
                }
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                TextButton(
                    onClick = viewModel::deleteRemoteBackups,
                    enabled = !uiState.isBackupBusy,
                ) {
                    Text("Delete Remote Backups")
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
            Button(
                onClick = { showRestoreDialog.value = true },
                enabled = !uiState.isBackupBusy,
            ) {
                Text("Restore from Backup")
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))
        }
    }

    val pendingRecoveryKey = viewModel.pendingRecoveryKey()

    // Any time a recovery key is being displayed (reveal / just-generated) or
    // entered (restore), clamp FLAG_SECURE on the hosting window. A conditional
    // SecureScreen() call is fine: Compose tracks the DisposableEffect and
    // cleans up when the branch leaves composition. iOS parity.
    if (revealedRecoveryKey.value != null ||
        pendingRecoveryKey != null ||
        showRestoreDialog.value
    ) {
        SecureScreen()
    }

    if (showRestoreDialog.value) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog.value = false },
            title = { Text("Restore Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm)) {
                    Text(
                        "Restore your encrypted chat history after sign-in using your recovery key. Leave the field blank if this device already stores it.",
                    )
                    OutlinedTextField(
                        value = restoreRecoveryKey.value,
                        onValueChange = { restoreRecoveryKey.value = it },
                        label = { Text("Recovery key") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val providedKey = restoreRecoveryKey.value.trim().takeIf { it.isNotEmpty() }
                        viewModel.restoreBackup(providedKey)
                        showRestoreDialog.value = false
                    },
                    enabled = !uiState.isBackupBusy,
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog.value = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    pendingRecoveryKey?.let { recoveryKey ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPendingBackup,
            title = { Text("Recovery Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm)) {
                    Text("Save this recovery key somewhere secure before continuing.")
                    Text(
                        text = recoveryKey,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            confirmButton = {
                Button(onClick = viewModel::confirmPendingBackup) {
                    Text("I saved it")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelPendingBackup) {
                    Text("Not now")
                }
            },
        )
    }

    revealedRecoveryKey.value?.let { recoveryKey ->
        AlertDialog(
            onDismissRequest = { revealedRecoveryKey.value = null },
            title = { Text("Recovery Key") },
            text = { Text(recoveryKey) },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(recoveryKey))
                        revealedRecoveryKey.value = null
                    },
                ) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { revealedRecoveryKey.value = null }) {
                    Text("Close")
                }
            },
        )
    }
}
