package com.sanchr.feature.chats.share

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrTextField
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.feature.chats.media.AttachmentPreparer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where content shared from another app lands: choose one or more chats,
 * add a caption, send.
 *
 * Mirrors iOS's share extension flow (picker, then composer). It runs
 * inside the app rather than a separate process, so App Lock already
 * guards it — sharing into a locked app should not be a way past the lock.
 */
@Composable
fun ShareTargetScreen(
    content: SharedContent,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShareTargetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(content) {
        if (content is SharedContent.Attachments) viewModel.seedCaption(content.caption)
    }

    uiState.outcome?.let { outcome ->
        LaunchedEffect(outcome) {
            Toast.makeText(context, outcome.message(), Toast.LENGTH_SHORT).show()
            onFinished()
        }
    }

    ShareTargetContent(
        uiState = uiState,
        content = content,
        onBack = { if (uiState.step == ShareStep.Picking) onFinished() else viewModel.backToPicking() },
        onQueryChanged = viewModel::onQueryChanged,
        onToggle = viewModel::toggleSelected,
        onNext = viewModel::proceedToCompose,
        onCaptionChanged = viewModel::onCaptionChanged,
        onSend = {
            when (content) {
                is SharedContent.Text -> viewModel.sendText(content.body)
                is SharedContent.Attachments -> {
                    val prepared =
                        withContext(Dispatchers.IO) {
                            content.uris.mapNotNull { AttachmentPreparer.prepare(context, Uri.parse(it)) }
                        }
                    viewModel.sendAttachments(prepared)
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * The share flow's rendering, with no dependency injection of its own, so a
 * UI test can drive the picker and the review step directly.
 */
@Composable
internal fun ShareTargetContent(
    uiState: ShareTargetUiState,
    content: SharedContent,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onToggle: (String) -> Unit,
    onNext: () -> Unit,
    onCaptionChanged: (String) -> Unit,
    onSend: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            SanchrTopBar(
                title = if (uiState.step == ShareStep.Picking) "Share with" else "Review",
                onNavigateBack = onBack,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(SanchrTheme.spacing.default)) {
            when (uiState.step) {
                ShareStep.Picking ->
                    ChatPicker(
                        uiState = uiState,
                        onQueryChanged = onQueryChanged,
                        onToggle = onToggle,
                        onNext = onNext,
                    )
                ShareStep.Composing ->
                    ShareComposer(
                        uiState = uiState,
                        content = content,
                        onCaptionChanged = onCaptionChanged,
                        onSend = onSend,
                    )
            }
        }
    }
}

@Composable
private fun ColumnScope.ChatPicker(
    uiState: ShareTargetUiState,
    onQueryChanged: (String) -> Unit,
    onToggle: (String) -> Unit,
    onNext: () -> Unit,
) {
    SanchrTextField(
        value = uiState.query,
        onValueChange = onQueryChanged,
        placeholder = "Search chats",
        modifier = Modifier.fillMaxWidth(),
    )
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        items(uiState.visibleConversations, key = { it.id }) { conversation ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(conversation.id) }
                        .padding(vertical = SanchrTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = conversation.id in uiState.selectedIds, onCheckedChange = { onToggle(conversation.id) })
                Text(
                    text = conversation.title ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    SanchrButton(
        text = if (uiState.selectedIds.isEmpty()) "Next" else "Next (${uiState.selectedIds.size})",
        onClick = onNext,
        enabled = uiState.canProceed,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ShareComposer(
    uiState: ShareTargetUiState,
    content: SharedContent,
    onCaptionChanged: (String) -> Unit,
    onSend: suspend () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.default),
    ) {
        Text(text = content.summary(), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "Sending to ${uiState.selectedIds.size} chat${if (uiState.selectedIds.size == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (content is SharedContent.Attachments) {
            SanchrTextField(
                value = uiState.caption,
                onValueChange = onCaptionChanged,
                placeholder = "Add a caption",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (uiState.isSending) {
            CircularProgressIndicator()
        } else {
            SanchrButton(
                text = "Send",
                onClick = { scope.launch { onSend() } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One line describing what is about to be sent. */
private fun SharedContent.summary(): String =
    when (this) {
        is SharedContent.Text -> body
        is SharedContent.Attachments -> if (uris.size == 1) "1 file" else "${uris.size} files"
    }

/** Honest about partial delivery: some chats can fail while others succeed. */
private fun ShareOutcome.message(): String =
    when {
        sent == 0 -> "Couldn't share"
        failed > 0 -> "Shared to $sent, failed for $failed"
        sent == 1 -> "Shared"
        else -> "Shared to $sent chats"
    }
