package com.sanchr.feature.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.common.Result
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.core.notifications.NotificationHandler
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.SendMessageUseCase
import com.sanchr.proto.messaging.EncryptedEnvelope
import com.sanchr.proto.messaging.MessagingServiceClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val messagingServiceClient: MessagingServiceClient,
    private val sessionManager: SessionManager,
    private val encryptionHelper: ChatEncryptionHelper,
    private val notificationHandler: NotificationHandler,
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    private val _uiState = MutableStateFlow(
        ChatDetailUiState(
            currentUserId = sessionManager.getUserId() ?: "",
        ),
    )
    val uiState: StateFlow<ChatDetailUiState> = _uiState.asStateFlow()

    init {
        observeMessages()
        markAsRead()
        clearNotificationsForConversation()
        connectMessageStream()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            messageRepository.observeMessages(conversationId).collect { messages ->
                _uiState.update {
                    it.copy(
                        messages = messages.map { msg -> msg.toUiModel() },
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun markAsRead() {
        viewModelScope.launch {
            messageRepository.markAsRead(conversationId)
        }
    }

    /**
     * Clears any pending system notifications for this conversation and
     * updates the summary badge count. Called when the user opens the chat.
     */
    private fun clearNotificationsForConversation() {
        notificationHandler.cancelNotificationsForConversation(conversationId)
    }

    /**
     * Placeholder for bidirectional streaming connection.
     * Listens for incoming server events (typing indicators, receipts, presence).
     */
    private fun connectMessageStream() {
        // TODO: Set up bidi stream with messagingServiceClient.messageStream()
        // val clientEvents = MutableSharedFlow<ClientEvent>()
        // val serverEvents = messagingServiceClient.messageStream(clientEvents)
        // viewModelScope.launch {
        //     serverEvents.collect { event ->
        //         when (event) {
        //             is ServerEvent.Typing -> _uiState.update { it.copy(peerTyping = event.indicator?.isTyping == true) }
        //             is ServerEvent.Receipt -> { /* update message statuses */ }
        //             is ServerEvent.Presence -> { /* update online status */ }
        //             is ServerEvent.Message -> event.envelope?.let { decryptIncomingMessage(it) }
        //             is ServerEvent.PreKeyCountLow -> replenishPreKeys()
        //         }
        //     }
        // }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }

        // Send typing indicator
        viewModelScope.launch {
            try {
                // TODO: Emit typing event through the bidi stream
                // clientEvents.emit(ClientEvent.Typing(conversationId = conversationId, isTyping = text.isNotBlank()))
            } catch (_: Exception) {
                // Typing indicator failure is non-critical
            }
        }
    }

    /**
     * Sends an encrypted message to the conversation.
     *
     * Flow:
     * 1. Get recipient IDs from the conversation participants, excluding self.
     * 2. For each recipient: check session -> establish via X3DH if needed -> encrypt.
     * 3. Build list of DeviceMessage with per-device ciphertexts.
     * 4. Send via MessagingService with encrypted DeviceMessages.
     */
    fun sendMessage() {
        val content = _uiState.value.inputText.trim()
        if (content.isBlank()) return

        _uiState.update { it.copy(inputText = "", isSending = true) }

        viewModelScope.launch {
            try {
                // Get recipient IDs from conversation participants, excluding self
                val currentUserId = sessionManager.getUserId() ?: ""
                val conversation = _uiState.value.conversation
                val recipientIds = conversation?.participants
                    ?.map { it.id }
                    ?.filter { it != currentUserId }
                    ?: emptyList()

                if (recipientIds.isNotEmpty()) {
                    // Encrypt for all recipient devices
                    val deviceMessages = encryptionHelper.encryptMessage(
                        plaintext = content,
                        recipientIds = recipientIds,
                    )

                    // Send encrypted message via gRPC
                    val request = com.sanchr.proto.messaging.SendMessageRequest(
                        conversationId = conversationId,
                        recipientUserId = recipientIds.firstOrNull() ?: "",
                        contentType = "text",
                        messages = deviceMessages,
                        clientMessageId = java.util.UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                    )
                    messagingServiceClient.sendMessage(request)
                }

                // Also persist locally via use case for optimistic UI
                when (val result = sendMessageUseCase(conversationId, content)) {
                    is Result.Success -> {
                        _uiState.update { it.copy(isSending = false) }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                error = "Failed to send message",
                            )
                        }
                    }
                    is Result.Loading -> { /* no-op */ }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        error = e.message ?: "Encryption failed",
                    )
                }
            }
        }
    }

    /**
     * Decrypts an incoming encrypted envelope and processes the plaintext message.
     * Called when the message stream delivers a new [ServerEvent.Message].
     */
    fun decryptIncomingMessage(envelope: EncryptedEnvelope) {
        viewModelScope.launch {
            try {
                val decrypted = encryptionHelper.decryptEnvelope(envelope)
                val plaintextString = String(decrypted.plaintext, Charsets.UTF_8)

                // The decrypted message should be persisted via the repository.
                // The observeMessages() flow will pick up the change automatically.
                messageRepository.insertDecryptedMessage(
                    conversationId = decrypted.conversationId,
                    messageId = decrypted.messageId,
                    senderId = decrypted.senderId,
                    content = plaintextString,
                    contentType = decrypted.contentType,
                    timestamp = decrypted.serverTimestamp,
                )

                // Check if pre-keys need replenishment after receiving a PreKey message
                encryptionHelper.checkPreKeyReplenishment()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to decrypt message: ${e.message}")
                }
            }
        }
    }

    fun loadMoreMessages() {
        val messages = _uiState.value.messages
        if (messages.isEmpty() || _uiState.value.isLoadingMore) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val oldestTimestamp = messages.minOfOrNull { it.timestamp } ?: 0L
            val olderMessages = messageRepository.loadMoreMessages(
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
        return MessageUiModel(
            id = id,
            text = when (content) {
                is MessageContent.Text -> (content as MessageContent.Text).body
                is MessageContent.Image -> (content as MessageContent.Image).caption ?: "[Image]"
                is MessageContent.Voice -> "[Voice message]"
                is MessageContent.File -> (content as MessageContent.File).fileName
                is MessageContent.Location -> (content as MessageContent.Location).label ?: "[Location]"
            },
            timestamp = timestamp.toEpochMilliseconds(),
            isFromMe = senderId == currentUser,
            status = when (status) {
                com.sanchr.core.model.MessageStatus.SENDING -> MessageStatus.SENDING
                com.sanchr.core.model.MessageStatus.SENT -> MessageStatus.SENT
                com.sanchr.core.model.MessageStatus.DELIVERED -> MessageStatus.DELIVERED
                com.sanchr.core.model.MessageStatus.READ -> MessageStatus.READ
                com.sanchr.core.model.MessageStatus.FAILED -> MessageStatus.FAILED
            },
            contentType = when (content) {
                is MessageContent.Text -> "text"
                is MessageContent.Image -> "image"
                is MessageContent.Voice -> "voice"
                is MessageContent.File -> "file"
                is MessageContent.Location -> "location"
            },
        )
    }
}
