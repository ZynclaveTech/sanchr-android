package com.sanchr.feature.chats.share

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.common.Result
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.model.Conversation
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.SendAttachmentUseCase
import com.sanchr.domain.messaging.SendMessageUseCase
import com.sanchr.domain.messaging.media.AttachmentUploader
import com.sanchr.domain.messaging.media.VideoCompressor
import com.sanchr.feature.chats.media.AttachmentPreparer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the share flow is: pick the chats, then review, then it is gone. */
enum class ShareStep {
    Picking,
    Composing,
}

/** What the share screen is showing. */
data class ShareTargetUiState(
    val step: ShareStep = ShareStep.Picking,
    val conversations: List<Conversation> = emptyList(),
    val query: String = "",
    val selectedIds: Set<String> = emptySet(),
    val caption: String = "",
    val isSending: Boolean = false,
    /** Set once the send finishes; the screen reports it and closes. */
    val outcome: ShareOutcome? = null,
) {
    /** Conversations matching the search box, in the order the list already had them. */
    val visibleConversations: List<Conversation>
        get() =
            if (query.isBlank()) {
                conversations
            } else {
                conversations.filter { it.title.orEmpty().contains(query.trim(), ignoreCase = true) }
            }

    val canProceed: Boolean get() = selectedIds.isNotEmpty() && !isSending
}

/** How many chats the share reached, so the screen can be honest about partial failure. */
data class ShareOutcome(
    val sent: Int,
    val failed: Int,
)

/**
 * Drives the system share sheet's landing screen: choose chats, add a
 * caption, send.
 *
 * The content itself is supplied by the caller rather than injected,
 * because it arrives on an Intent that only the activity can see.
 */
@HiltViewModel
class ShareTargetViewModel
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val sendMessageUseCase: SendMessageUseCase,
        private val sendAttachmentUseCase: SendAttachmentUseCase,
        private val videoCompressor: VideoCompressor,
        private val userPreferences: UserPreferences,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ShareTargetUiState())
        val uiState: StateFlow<ShareTargetUiState> = _uiState.asStateFlow()

        /**
         * Reads a shared file into the shape the uploader wants, re-encoding a
         * video first.
         *
         * The screen used to call `AttachmentPreparer` itself, which skipped
         * the re-encode and the Low Data Mode setting for anything arriving
         * from another app — the one path where a 4K clip is most likely to
         * turn up, since it comes straight from the gallery.
         */
        suspend fun prepareAttachment(uri: Uri): AttachmentUploader.Prepared? =
            withContext(Dispatchers.IO) {
                val mimeType = runCatching { appContext.contentResolver.getType(uri) }.getOrNull().orEmpty()
                if (!mimeType.startsWith("video/")) {
                    return@withContext AttachmentPreparer.prepare(appContext, uri)
                }
                val sourceBytes = AttachmentPreparer.reportedSize(appContext, uri) ?: 0L
                val lowData = runCatching { userPreferences.lowDataMode.first() }.getOrDefault(false)
                val compressed = runCatching { videoCompressor.compressedForSending(uri, sourceBytes, lowData) }.getOrNull()
                try {
                    AttachmentPreparer.prepare(appContext, uri, readFrom = compressed?.let(Uri::fromFile) ?: uri)
                } finally {
                    compressed?.delete()
                }
            }

        init {
            viewModelScope.launch {
                messageRepository
                    .observeConversations()
                    .catch { /* DB closed during logout */ }
                    .collect { conversations -> _uiState.update { it.copy(conversations = conversations) } }
            }
        }

        fun onQueryChanged(query: String) = _uiState.update { it.copy(query = query) }

        fun toggleSelected(conversationId: String) =
            _uiState.update { state ->
                val next =
                    if (conversationId in state.selectedIds) {
                        state.selectedIds - conversationId
                    } else {
                        state.selectedIds + conversationId
                    }
                state.copy(selectedIds = next)
            }

        fun onCaptionChanged(caption: String) = _uiState.update { it.copy(caption = caption) }

        /** Moves to the review step; the picker's selection is fixed from here. */
        fun proceedToCompose() {
            if (_uiState.value.selectedIds.isEmpty()) return
            _uiState.update { it.copy(step = ShareStep.Composing) }
        }

        fun backToPicking() = _uiState.update { it.copy(step = ShareStep.Picking) }

        /** Seeds the caption from text the sharing app sent alongside its files. */
        fun seedCaption(caption: String?) {
            if (caption.isNullOrBlank() || _uiState.value.caption.isNotEmpty()) return
            _uiState.update { it.copy(caption = caption) }
        }

        /**
         * Sends [text] to every chosen chat.
         *
         * Each target is counted separately so one failed conversation does
         * not report the whole share as lost, or as delivered.
         */
        fun sendText(text: String) =
            send { conversationId ->
                sendMessageUseCase(conversationId, text) is Result.Success
            }

        /**
         * Sends already-read files to every chosen chat, with the caption on
         * the first one only, so a set of photos does not repeat it.
         */
        fun sendAttachments(prepared: List<AttachmentUploader.Prepared>) {
            if (prepared.isEmpty()) {
                _uiState.update { it.copy(outcome = ShareOutcome(sent = 0, failed = 1)) }
                return
            }
            val caption =
                _uiState.value.caption
                    .trim()
                    .takeIf { it.isNotEmpty() }
            send { conversationId ->
                prepared
                    .mapIndexed { index, file ->
                        val withCaption = if (index == 0 && caption != null) file.withCaption(caption) else file
                        sendAttachmentUseCase(conversationId, withCaption) is Result.Success
                    }.all { it }
            }
        }

        private fun send(one: suspend (String) -> Boolean) {
            val targets = _uiState.value.selectedIds.toList()
            if (targets.isEmpty() || _uiState.value.isSending) return
            _uiState.update { it.copy(isSending = true) }
            viewModelScope.launch {
                var sent = 0
                var failed = 0
                targets.forEach { target ->
                    val ok = runCatching { one(target) }.getOrDefault(false)
                    if (ok) sent++ else failed++
                }
                _uiState.update { it.copy(isSending = false, outcome = ShareOutcome(sent = sent, failed = failed)) }
            }
        }
    }
