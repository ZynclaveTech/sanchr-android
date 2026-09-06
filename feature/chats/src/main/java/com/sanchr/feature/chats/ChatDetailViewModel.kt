package com.sanchr.feature.chats

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.common.Result
import com.sanchr.core.crypto.verify.SafetyNumberManager
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.model.ContactCard
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.core.model.MessageReaction
import com.sanchr.core.network.ConnectivityMonitor
import com.sanchr.core.network.link.LinkPreview
import com.sanchr.core.network.link.LinkPreviewFetcher
import com.sanchr.core.notifications.NotificationHandler
import com.sanchr.domain.messaging.ConsumeViewOnceUseCase
import com.sanchr.domain.messaging.ForwardMessageUseCase
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.PresenceStore
import com.sanchr.domain.messaging.SendAttachmentUseCase
import com.sanchr.domain.messaging.SendMessageUseCase
import com.sanchr.domain.messaging.SendReadReceiptUseCase
import com.sanchr.domain.messaging.ToggleReactionUseCase
import com.sanchr.domain.messaging.UnsupportedContentException
import com.sanchr.domain.messaging.media.AttachmentDownloader
import com.sanchr.domain.messaging.media.AttachmentUploader
import com.sanchr.domain.messaging.media.MediaAutoDownloadPolicy
import com.sanchr.feature.chats.location.LocationPayload
import com.sanchr.sync.realtime.RealtimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
        private val toggleReactionUseCase: ToggleReactionUseCase,
        private val consumeViewOnceUseCase: ConsumeViewOnceUseCase,
        private val forwardMessageUseCase: ForwardMessageUseCase,
        private val presenceStore: PresenceStore,
        private val sessionManager: SessionManager,
        private val realtimeManager: RealtimeManager,
        private val notificationHandler: NotificationHandler,
        private val userPreferences: UserPreferences,
        private val linkPreviewFetcher: LinkPreviewFetcher,
        private val safetyNumbers: SafetyNumberManager,
        private val connectivityMonitor: ConnectivityMonitor,
    ) : ViewModel() {
        private companion object {
            const val TAG = "ChatDetailViewModel"
            const val PRESENCE_REFRESH_MS = 15_000L
            const val SEARCH_DEBOUNCE_MS = 300L
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

        /** The loaded transcript as domain rows, keyed by id, for actions that need more than the UI model. */
        private var domainMessages: Map<String, Message> = emptyMap()

        init {
            observeConversation()
            observeMessages()
            observeForwardTargets()
            observeTyping()
            observePresence()
            observeLinkPreviewSetting()
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
                _uiState.update { it.copy(directPeerId = peer) }
                refreshIdentityChangeState()
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
                        domainMessages = messages.associateBy { it.id }
                        _uiState.update { state ->
                            state.copy(
                                messages = messages.toUiModels(state.conversation),
                                isLoading = false,
                            )
                        }
                        markReadIfNewInbound(messages)
                    }
            }
        }

        /**
         * Typing indicators are a mutual courtesy, so the setting governs both
         * directions: a user who does not broadcast that they are typing does
         * not see the other side's either, as on iOS.
         */
        private fun observeTyping() {
            viewModelScope.launch {
                combine(
                    realtimeManager.typingCache,
                    userPreferences.typingIndicatorsEnabled,
                ) { cache, enabled ->
                    enabled && cache[conversationId]?.isTyping == true
                }.collect { typing ->
                    _uiState.update { state -> state.copy(peerTyping = typing) }
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
        private var newestInboundSeen: String? = null

        /** While the chat is on screen, a message that just arrived is read: clear the badge the way iOS does on append. */
        private suspend fun markReadIfNewInbound(messages: List<Message>) {
            val self = sessionManager.getUserId() ?: return
            val newestInbound = messages.lastOrNull { it.senderId != self }?.id ?: return
            if (newestInbound == newestInboundSeen) return
            val first = newestInboundSeen == null
            newestInboundSeen = newestInbound
            if (first) return // the initial load is handled by markAsRead() in init
            runCatching { messageRepository.markAsRead(conversationId) }
        }

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
            viewModelScope.launch {
                // Read per keystroke rather than cached: the toggle is in
                // Settings, and a chat left open must stop broadcasting the
                // moment it is turned off, not on next launch.
                val enabled = runCatching { userPreferences.typingIndicatorsEnabled.first() }.getOrDefault(true)
                if (enabled) realtimeManager.sendTypingIndicator(conversationId, text.isNotBlank())
            }
        }

        fun sendMessage() {
            val content = _uiState.value.inputText.trim()
            if (content.isBlank()) return
            val replyToId = takePendingReply()
            _uiState.update { it.copy(inputText = "") }
            dispatchSend(content, contentType = "text", replyToId = replyToId)
        }

        /** Shares a contact card the way iOS does: a bare `{"name","phoneNumber"}` body typed `contact`. */
        fun sendContact(card: ContactCard) {
            if (_uiState.value.isSending) return
            dispatchSend(card.encode(), contentType = ContactCard.CONTENT_TYPE, replyToId = takePendingReply())
        }

        /**
         * The reply being composed, cleared as it is handed over: every send
         * consumes it, so the banner does not linger and quote the next
         * message too.
         */
        private fun takePendingReply(): String? {
            val replyToId = _uiState.value.replyingTo?.id ?: return null
            _uiState.update { it.copy(replyingTo = null) }
            return replyToId
        }

        private fun dispatchSend(
            content: String,
            contentType: String,
            replyToId: String? = null,
        ) {
            _uiState.update { it.copy(isSending = true) }
            viewModelScope.launch {
                when (val result = sendMessageUseCase(conversationId, content, contentType, replyToId)) {
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

        /**
         * Sends a reviewed batch of photos, the caption on the first only so a
         * set does not repeat it, and stops at the first failure rather than
         * reporting success for a partial send.
         */
        fun sendAttachments(prepared: List<AttachmentUploader.Prepared>) {
            if (prepared.isEmpty() || _uiState.value.isSending) return
            val replyToId = takePendingReply()
            _uiState.update { it.copy(isSending = true, uploadProgress = 0f) }
            viewModelScope.launch {
                var failure: String? = null
                prepared.forEachIndexed { index, file ->
                    if (failure != null) return@forEachIndexed
                    val result =
                        sendAttachmentUseCase(conversationId, file, replyToId.takeIf { index == 0 }) { fraction ->
                            _uiState.update { it.copy(uploadProgress = (index + fraction) / prepared.size) }
                        }
                    if (result is Result.Error) failure = result.exception.message ?: "Failed to send attachment"
                }
                _uiState.update { it.copy(isSending = false, uploadProgress = null, error = failure) }
            }
        }

        /**
         * Sends the user's current position as a `location` message.
         *
         * The body is the same JSON iOS writes, so a pin sent from here
         * renders as a pin there rather than as unreadable text.
         */
        fun sendLocation(
            latitude: Double,
            longitude: Double,
        ) {
            val replyToId = takePendingReply()
            viewModelScope.launch {
                val body = LocationPayload.encode(latitude, longitude)
                when (val result = sendMessageUseCase(conversationId, body, "location", replyToId)) {
                    is Result.Error ->
                        _uiState.update { it.copy(error = result.exception.message ?: "Couldn't send your location") }
                    else -> Unit
                }
            }
        }

        /** Encrypts and sends a picked file as an attachment message. */
        fun sendAttachment(prepared: AttachmentUploader.Prepared) {
            if (_uiState.value.isSending) return
            val replyToId = takePendingReply()
            _uiState.update { it.copy(isSending = true, uploadProgress = 0f) }
            viewModelScope.launch {
                val result =
                    sendAttachmentUseCase(conversationId, prepared, replyToId) { fraction ->
                        _uiState.update { it.copy(uploadProgress = fraction) }
                    }
                when (result) {
                    is Result.Success -> _uiState.update { it.copy(isSending = false, uploadProgress = null) }
                    is Result.Error ->
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                uploadProgress = null,
                                error =
                                    result.exception.message ?: "Failed to send attachment",
                            )
                        }
                    is Result.Loading -> Unit
                }
            }
        }

        /**
         * Re-reads whether the peer's key changed without review. Sends fail
         * closed while it has, so the banner has to reflect the store rather
         * than a cached flag that a review elsewhere would leave stale.
         */
        fun refreshIdentityChangeState() {
            val peer = presencePeer ?: return
            viewModelScope.launch {
                val pending = runCatching { safetyNumbers.hasPendingIdentityChange(peer) }.getOrDefault(false)
                _uiState.update { it.copy(identityChangePending = pending) }
            }
        }

        /**
         * The user reviewed the key change and chose to carry on. Unblocks
         * sending; it does not mark the contact verified, which needs the
         * safety numbers actually compared.
         */
        fun acceptIdentityChange() {
            val peer = presencePeer ?: return
            viewModelScope.launch {
                runCatching { safetyNumbers.acceptIdentityChange(peer) }
                refreshIdentityChangeState()
            }
        }

        /**
         * Whether a bubble may fetch its own media without being tapped.
         *
         * Read at the moment of the fetch rather than cached, so the network
         * changing under a chat that is already open is respected.
         */
        fun mayAutoDownload(): Boolean {
            val setting = _uiState.value.mediaAutoDownload
            return MediaAutoDownloadPolicy.shouldAutoDownload(setting, connectivityMonitor.isMetered)
        }

        /** Whether this attachment is already on disk, so showing it costs nothing. */
        fun isCached(message: MessageUiModel): Boolean {
            val attachment = message.attachment ?: return false
            return runCatching { attachmentDownloader.isCached(message.id, attachment) }.getOrDefault(false)
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

        /** Maps the transcript, resolving each reply's quote against the same batch (as iOS builds `ReplyQuote`s). */
        private fun List<Message>.toUiModels(conversation: Conversation?): List<MessageUiModel> {
            val currentUser = sessionManager.getUserId() ?: ""
            val byId = associateBy { it.id }
            return map { message ->
                val quoted = message.replyToId?.let(byId::get)
                message.toUiModel().copy(
                    quote =
                        quoted?.let {
                            ReplyQuote(
                                authorName = if (it.senderId == currentUser) "You" else conversation?.title.orEmpty().ifBlank { "Contact" },
                                preview = it.content.displayText(),
                            )
                        },
                )
            }
        }

        private fun observeForwardTargets() {
            viewModelScope.launch {
                messageRepository
                    .observeConversations()
                    .catch { /* DB closed during logout */ }
                    .collect { conversations -> _uiState.update { it.copy(forwardTargets = conversations) } }
            }
        }

        /** Forwards [message] to [targetConversationIds] at once (iOS `forwardMessage`); reports the outcome as a notice. */
        fun forward(
            message: MessageUiModel,
            targetConversationIds: List<String>,
        ) {
            val domain = domainMessages[message.id] ?: return
            viewModelScope.launch {
                try {
                    val outcome = forwardMessageUseCase(domain, targetConversationIds)
                    val notice =
                        when {
                            outcome.sent == 0 -> null
                            outcome.failed == 0 -> "Forwarded to ${outcome.sent} ${if (outcome.sent == 1) "chat" else "chats"}"
                            else -> "Forwarded to ${outcome.sent}, failed for ${outcome.failed}"
                        }
                    _uiState.update { it.copy(notice = notice, error = if (outcome.sent == 0) "Could not forward" else null) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: UnsupportedContentException) {
                    _uiState.update { it.copy(error = "This message can't be forwarded") }
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.message ?: "Could not forward") }
                }
            }
        }

        /** Removes a message locally, and for everyone when [forEveryone] (our own messages only). */
        fun deleteMessage(
            message: MessageUiModel,
            forEveryone: Boolean,
        ) {
            viewModelScope.launch {
                try {
                    messageRepository.deleteMessage(message.id, forEveryone = forEveryone && message.isFromMe)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.message ?: "Could not delete message") }
                }
            }
        }

        // ------------------------------------------------------------------
        // In-chat search (iOS `scheduleSearch`: debounced, newest first, wraps around)
        // ------------------------------------------------------------------

        private var searchJob: Job? = null

        fun openSearch() {
            _uiState.update { it.copy(search = it.search ?: ChatSearchState()) }
        }

        fun closeSearch() {
            searchJob?.cancel()
            _uiState.update { it.copy(search = null) }
        }

        fun onSearchQueryChanged(query: String) {
            _uiState.update { it.copy(search = (it.search ?: ChatSearchState()).copy(query = query)) }
            searchJob?.cancel()
            if (query.isBlank()) {
                _uiState.update { it.copy(search = it.search?.copy(resultIds = emptyList(), currentIndex = 0)) }
                return
            }
            searchJob =
                viewModelScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    val ids = runCatching { messageRepository.searchMessages(conversationId, query) }.getOrDefault(emptyList())
                    _uiState.update { state ->
                        val search = state.search ?: return@update state
                        if (search.query != query) state else state.copy(search = search.copy(resultIds = ids, currentIndex = 0))
                    }
                }
        }

        fun nextSearchResult() = stepSearch(+1)

        fun previousSearchResult() = stepSearch(-1)

        private fun stepSearch(delta: Int) {
            _uiState.update { state ->
                val search = state.search ?: return@update state
                if (search.resultIds.isEmpty()) return@update state
                val size = search.resultIds.size
                state.copy(search = search.copy(currentIndex = ((search.currentIndex + delta) % size + size) % size))
            }
        }

        fun dismissNotice() {
            _uiState.update { it.copy(notice = null) }
        }

        private fun observeLinkPreviewSetting() {
            viewModelScope.launch {
                userPreferences.linkPreviewsEnabled.collect { enabled -> _uiState.update { it.copy(linkPreviewsEnabled = enabled) } }
            }
            viewModelScope.launch {
                // The full-screen viewer honours the same toggle as the rest of
                // the app; otherwise opening a photo would be the one window a
                // screenshot could still catch.
                userPreferences.screenshotProtectionEnabled.collect { on ->
                    _uiState.update { it.copy(screenshotProtectionEnabled = on) }
                }
            }
            viewModelScope.launch {
                userPreferences.mediaAutoDownload.collect { setting ->
                    _uiState.update { it.copy(mediaAutoDownload = setting) }
                }
            }
            viewModelScope.launch {
                // The chat's own choice wins; "no choice" follows the
                // account-wide one, so changing that still reaches every chat
                // that never picked its own.
                combine(
                    messageRepository.observeConversation(conversationId),
                    userPreferences.chatWallpaper,
                ) { conversation, accountWide ->
                    conversation?.wallpaper ?: accountWide
                }.catch { }
                    .collect { name -> _uiState.update { it.copy(wallpaper = name) } }
            }
        }

        /** The card for a link in a bubble; cached, and only called while the privacy setting is on. */
        suspend fun linkPreview(url: String): LinkPreview? = linkPreviewFetcher.preview(url)

        fun setReply(message: MessageUiModel) {
            _uiState.update { it.copy(replyingTo = message) }
        }

        fun clearReply() {
            _uiState.update { it.copy(replyingTo = null) }
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
                contact = (content as? MessageContent.Contact)?.let { ContactCard(it.name, it.phoneNumber) },
                reactions = reactions.toChips(currentUser),
                replyToId = replyToId,
                isViewOnce = content.attachmentOrNull()?.isViewOnce == true,
            )
        }

        /** The viewer closed view-once media: wipe the bytes and leave a "Viewed" tombstone (as iOS). */
        fun consumeViewOnce(message: MessageUiModel) {
            val attachment = message.attachment ?: return
            viewModelScope.launch {
                try {
                    consumeViewOnceUseCase(message.id, attachment)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not consume view-once ${message.id}: ${e.message}")
                }
            }
        }

        private fun List<MessageReaction>.toChips(selfUserId: String): List<ReactionChip> =
            groupBy { it.emoji }.map { (emoji, users) -> ReactionChip(emoji, users.size, users.any { it.userId == selfUserId }) }

        /** Adds our [emoji] to a message, or removes it when already there (as iOS). */
        fun toggleReaction(
            messageId: String,
            emoji: String,
        ) {
            viewModelScope.launch {
                try {
                    toggleReactionUseCase(conversationId, messageId, emoji)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.message ?: "Could not send reaction") }
                }
            }
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
                is MessageContent.Contact -> name.ifBlank { phoneNumber }
                is MessageContent.System -> text
            }

        private fun MessageContent.attachmentOrNull(): MediaAttachment? =
            when (this) {
                is MessageContent.Image -> attachment
                is MessageContent.Voice -> attachment
                is MessageContent.File -> attachment
                is MessageContent.Text, is MessageContent.Location, is MessageContent.Contact, is MessageContent.System -> null
            }

        private fun MessageContent.contentTypeTag(): String =
            when (this) {
                is MessageContent.Text -> "text"
                is MessageContent.Image -> "image"
                is MessageContent.Voice -> "voice"
                is MessageContent.File -> "file"
                is MessageContent.Location -> "location"
                is MessageContent.Contact -> ContactCard.CONTENT_TYPE
                is MessageContent.System -> MessageContent.System.CONTENT_TYPE
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
