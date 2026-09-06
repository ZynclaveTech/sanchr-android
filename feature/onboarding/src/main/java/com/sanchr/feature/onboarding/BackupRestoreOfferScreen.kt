package com.sanchr.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrTextField
import com.sanchr.core.designsystem.theme.SanchrTheme
import java.text.DateFormat
import java.util.Date

/**
 * Offers to restore a backup after signing in on a fresh install.
 *
 * Android only exposed restore in Settings → Chats. Someone reinstalling has
 * no reason to go there: they sign in, find an empty app, and reasonably
 * conclude their history is gone. It was on the server the whole time.
 *
 * Skippable, and skipping destroys nothing — the backup stays where it is and
 * Settings still offers it later.
 */
@Composable
fun BackupRestoreOfferScreen(
    onFinished: () -> Unit,
    viewModel: BackupRestoreOfferViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Nothing to offer: leave without ever showing a frame, so a user with no
    // backup does not see a screen flash on the way to their chats.
    LaunchedEffect(state) {
        if (state is BackupRestoreOfferState.Nothing) onFinished()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(SanchrTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.default),
    ) {
        when (val current = state) {
            BackupRestoreOfferState.Checking, BackupRestoreOfferState.Nothing ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            is BackupRestoreOfferState.Found -> FoundBody(current, viewModel, onFinished)

            is BackupRestoreOfferState.Restored -> RestoredBody(current, onFinished)
        }
    }
}

@Composable
private fun ColumnScope.FoundBody(
    state: BackupRestoreOfferState.Found,
    viewModel: BackupRestoreOfferViewModel,
    onFinished: () -> Unit,
) {
    Text(
        text = "Restore your chats",
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text =
            buildString {
                append("There is an encrypted backup for this account")
                state.backup.createdAtMillis?.let {
                    append(", made ")
                    append(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)))
                }
                append(".")
            },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        // Said plainly: the key is the only thing that can open it, and we
        // genuinely cannot help if it is gone.
        text = "Enter your recovery key to unlock it. Without the key the backup cannot be read, by you or by us.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SanchrTextField(
        value = state.recoveryKey,
        onValueChange = viewModel::onRecoveryKeyChanged,
        placeholder = "Recovery key",
        modifier = Modifier.fillMaxWidth(),
    )

    state.error?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Box(modifier = Modifier.weight(1f))

    SanchrButton(
        text = if (state.isRestoring) "Restoring…" else "Restore",
        onClick = viewModel::restore,
        enabled = !state.isRestoring && state.recoveryKey.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(
        onClick = onFinished,
        enabled = !state.isRestoring,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Not "No thanks": skipping deletes nothing, and the user should not
        // be left thinking they have just refused their history for good.
        Text("Not now — I can restore later from Settings")
    }
}

@Composable
private fun ColumnScope.RestoredBody(
    state: BackupRestoreOfferState.Restored,
    onFinished: () -> Unit,
) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
        ) {
            Text(
                text = "Your chats are back",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            state.backupAtMillis?.let {
                Text(
                    text = "Restored from the backup made ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    SanchrButton(
        text = "Continue",
        onClick = onFinished,
        modifier = Modifier.fillMaxWidth(),
    )
}
