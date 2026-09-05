package com.sanchr.feature.chats

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.common.Result
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.core.notifications.NotificationHandler
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.PresenceStore
import com.sanchr.domain.messaging.SendAttachmentUseCase
import com.sanchr.domain.messaging.SendMessageUseCase
import com.sanchr.domain.messaging.SendReadReceiptUseCase
import com.sanchr.domain.messaging.media.AttachmentDownloader
import com.sanchr.domain.messaging.media.AttachmentUploader
import com.sanchr.sync.realtime.RealtimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val messageRepository: MessageRepository,
        private val sendMessageUseCase: SendMessageUseCase,
        private val sendAttachmentUseCase: SendAttachmentUseCase,
        private val attachmentDownloader: AttachmentDownloader,
        private val sendReadReceiptUseCase: SendReadReceiptUseCase,
        private val presenceStore: PresenceStore,
        private val sessionManager: SessionManager,
        private val realtimeManager: RealtimeManager,
        private val notificationHandler: NotificationHandler,
    ) : ViewModel() {
        private companion object {
            const val TAG = "ChatDetailViewModel"
            const val PRESENCE_REFRESH_MS = 15_000L
        }

        private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

        private val _uiState =
            MutableStateFlow(
                ChatDetailUiState(
                    currentUserId = sessionManager.getUserId() ?: "",
                ),
            )
        val uiState: StateFlow<ChatDetailUiState> = _uiState.asStateFlow()

        private var presencePeer: String? = null

        init {
            observeConversation()
            observeMessages()
            observeTyping()
            observePresence()
            markAsRead()
            clearNotificationsForConversation()
        }

        /**
         * A 1:1 chat on screen announces us to the peer and shows what they
         * last told us (`PresenceStore`). While there is a line to show it is
         * re-evaluated every 15 s so "Online" decays and "Last seen" ages.
         */
        private fun observePresence() {
            viewModelScope.launch {
                val self = sessionManager.getUserId().orEmpty()
                val peer = runCatching { messageRepository.oneToOneRecipient(conversationId, self) }.getOrNull()
                if (peer.isNullOrEmpty()) return@launch
                presencePeer = peer
                realtimeManager.trackPresencePeer(peer)
                presenceStore.presence
                    .map { it[peer] }
                    .distinctUntilChanged()
                    .collectLatest {
                        while (true) {
                            val line = presenceStore.statusLine(peer)
                            _uiState.update { it.copy(peerPresence = line) }
                            if (line == null) return@collectLatest
                            delay(PRESENCE_REFRESH_MS)
                        }
                    }
            }
        }

        override fun onCleared() {
            presencePeer?.let(realtimeManager::untrackPresencePeer)
            super.onCleared()
        }

        private fun observeConversation() {
            viewModelScope.launch {
                messageRepository
                    .observeConversation(conversationId)
                    .catch { /* DB closed during logout — nav teardown cancels this scope */ }
                    .collect { conversation ->
                        _uiState.update { state ->
                            state.copy(conversation = conversation)
                        }
                    }
            }
        }

        private fun observeMessages() {
            viewModelScope.launch {
                messageRepository
                    .observeMessages(conversationId)
                    .catch { /* DB closed during logout — nav teardown cancels this scope */ }
                    .collect { messages ->
                        _uiState.update { state ->
                            state.copy(
                                messages = messages.map { message -> message.toUiModel() },
                                isLoading = false,
                            )
                        }
                    }
            }
        }

        private fun observeTyping() {
            viewModelScope.launch {
                realtimeManager.typingCache.collect { cache ->
                    _uiState.update { state ->
                        state.copy(peerTyping = cache[conversationId]?.isTyping == true)
                    }
                }
            }
        }

        /**
         * Zeroes the conversation's unread count and sends a read receipt
         * naming the newest inbound message. Both are best-effort and
         * separately guarded, and each runs in its own coroutine so the
         * receipt's jitter never holds up the unread-count write. Fires once, from
         * `init` — a conversation left open while new inbound messages
         * arrive will not re-fire this and so will not send a further
         * receipt for them. Widening the trigger (e.g. re-firing per new
         * message) is a behaviour change beyond this task's scope.
         */
        private fun markAsRead() {
            viewModelScope.launch {
                try {
                    messageRepository.markAsRead(conversationId)
                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    // Same reasoning as the receipt below: an unsupervised
                    // coroutine, so a DB failure here would take down the
                    // process. The count is recomputed from the message rows
                    // on the next open, so dropping this write only leaves a
                    // stale badge.
                }
            }
            viewModelScope.launch {
                sendReadReceiptForNewestInboundMessage()
            }
        }

        /**
         * Resolves the newest inbound message — the [Message] with the
         * greatest [Message.timestamp] whose [Message.senderId] is not the
         * current user — and asks [SendReadReceiptUseCase] to send a receipt
         * naming it. Android's `markAsRead` is conversation-wide, but a
         * receipt is per-message; the newest inbound message is the one the
         * peer most wants confirmation of, mirroring iOS's explicit
         * `upToMessageId`.
         *
         * Reads directly from [MessageRepository.observeMessages] rather
         * than [uiState]'s already-mapped list: that list is populated by
         * [observeMessages]'s own collector, launched separately in `init`,
         * and may still be empty by the time this runs. A conversation with
         * no messages, or whose messages are all outbound (e.g. the peer
         * has not sent anything yet), resolves no inbound message and sends
         * nothing. Sending is best-effort: [SendReadReceiptUseCase] never
         * throws (other than [kotlinx.coroutines.CancellationException]),
         * honours the read-receipts preference and the 1:1-only rule
         * itself, and jitters up to 3 s before it sends — running here in
         * its own coroutine so that delay never blocks screen rendering.
         * Resolving that message is guarded to the same standard, because it
         * runs in an unsupervised [viewModelScope] coroutine: an escaping
         * exception would reach the scope's handler and take down the
         * process rather than merely lose a receipt.
         */
        private suspend fun sendReadReceiptForNewestInboundMessage() {
            try {
                val currentUserId = sessionManager.getUserId() ?: return
                val newestInboundMessage =
                    messageRepository
                        .observeMessages(conversationId)
                        .first()
                        .filter { it.senderId != currentUserId }
                        .maxByOrNull { it.timestamp }
                        ?: return
                sendReadReceiptUseCase(conversationId, newestInboundMessage.id)
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Resolving the message is best-effort, like the send itself:
                // a DB failure propagates out of first(), and a flow that
                // completes without emitting makes first() throw
                // NoSuchElementException. Neither should crash the screen over
                // an unsent receipt.
            }
        }

        private fun clearNotificationsForConversation() {
            notificationHandler.cancelNotificationsForConversation(conversationId)
        }

        fun onInputTextChanged(text: String) {
            _uiState.update { it.copy(inputText = text) }
            realtimeManager.sendTypingIndicator(conversationId, text.isNotBlank())
        }

        fun sendMessage() {
            val content = _uiState.value.inputText.trim()
            if (content.isBlank()) return

            _uiState.update { it.copy(inputText = "", isSending = true) }

            viewModelScope.launch {
                when (val result = sendMessageUseCase(conversationId, content)) {
                    is Result.Success -> {
                        _uiState.update { it.copy(isSending = false) }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                error = result.exception.message ?: "Failed to send message",
                            )
                        }
                    }
                    is Result.Loading -> Unit
                }
            }
        }

        /** Encrypts and sends a picked file as an attachment message. */
        fun sendAttachment(prepared: AttachmentUploader.Prepared) {
            if (_uiState.value.isSending) return
            _uiState.update { it.copy(isSending = true) }
            viewModelScope.launch {
                when (val result = sendAttachmentUseCase(conversationId, prepared)) {
                    is Result.Success -> _uiState.update { it.copy(isSending = false) }
                    is Result.Error ->
                        _uiState.update {
                            it.copy(isSending = false, error = result.exception.message ?: "Failed to send attachment")
                        }
                    is Result.Loading -> Unit
                }
            }
        }

        /** The decrypted file for a message's attachment, downloading it if needed. */
        suspend fun openAttachment(message: MessageUiModel): File? {
            val attachment = message.attachment ?: return null
            return try {
                attachmentDownloader.open(message.id, attachment)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not open attachment for ${message.id}: ${e.message}")
                _uiState.update { it.copy(error = e.message ?: "Could not open attachment") }
                null
            }
        }

        fun loadMoreMessages() {
            val messages = _uiState.value.messages
            if (messages.isEmpty() || _uiState.value.isLoadingMore) return

            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingMore = true) }
                val oldestTimestamp = messages.minOfOrNull { it.timestamp } ?: 0L
                val olderMessages =
                    messageRepository.loadMoreMessages(
                        conversationId = conversationId,
                        beforeTimestamp = oldestTimestamp,
                        limit = 50,
                    )
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        hasMoreMessages = olderMessages.size == 50,
                    )
                }
            }
        }

        fun dismissError() {
            _uiState.update { it.copy(error = null) }
        }

        private fun Message.toUiModel(): MessageUiModel {
            val currentUser = sessionManager.getUserId() ?: ""
            val uiStatus = status.toUiStatus()
            return MessageUiModel(
                id = id,
                text = content.displayText(),
                timestamp = timestamp.toEpochMilliseconds(),
                isFromMe = senderId == currentUser,
                status = uiStatus,
                contentType = content.contentTypeTag(),
                failureKind = failureClass.toFailureKind(uiStatus),
                failureReason = failureReason.takeIf { uiStatus == MessageStatus.FAILED },
                attachment = content.attachmentOrNull(),
            )
        }

        /**
         * Maps the domain-layer `FailureClass` name (stored as plain string
         * in `core:model`) to the UI taxonomy. Returns null when the row is
         * not in a FAILED state so non-terminal rows render without a
         * failure affordance.
         */
        private fun String?.toFailureKind(status: MessageStatus): FailureKind? {
            if (status != MessageStatus.FAILED) return null
            return when (this) {
                "UNTRUSTED_IDENTITY" -> FailureKind.UNTRUSTED_IDENTITY
                else -> FailureKind.GENERIC
            }
        }

        private fun MessageContent.displayText(): String =
            when (this) {
                is MessageContent.Text -> body
                is MessageContent.Image -> caption ?: "[Image]"
                is MessageContent.Voice -> "[Voice message]"
                is MessageContent.File -> fileName
                is MessageContent.Location -> label ?: "[Location]"
            }

        private fun MessageContent.attachmentOrNull(): MediaAttachment? =
            when (this) {
                is MessageContent.Image -> attachment
                is MessageContent.Voice -> attachment
                is MessageContent.File -> attachment
                is MessageContent.Text, is MessageContent.Location -> null
            }

        private fun MessageContent.contentTypeTag(): String =
            when (this) {
                is MessageContent.Text -> "text"
                is MessageContent.Image -> "image"
                is MessageContent.Voice -> "voice"
                is MessageContent.File -> "file"
                is MessageContent.Location -> "location"
            }

        private fun com.sanchr.core.model.MessageStatus.toUiStatus(): MessageStatus =
            when (this) {
                com.sanchr.core.model.MessageStatus.QUEUED -> MessageStatus.SENDING
                com.sanchr.core.model.MessageStatus.SENDING -> MessageStatus.SENDING
                com.sanchr.core.model.MessageStatus.SENT -> MessageStatus.SENT
                com.sanchr.core.model.MessageStatus.DELIVERED -> MessageStatus.DELIVERED
                com.sanchr.core.model.MessageStatus.READ -> MessageStatus.READ
                com.sanchr.core.model.MessageStatus.FAILED -> MessageStatus.FAILED
            }
    }
