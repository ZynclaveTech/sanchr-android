package com.sanchr.feature.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.common.Result
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.SendMessageUseCase
import com.sanchr.proto.messaging.ClientEvent
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
        //             else -> { }
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
                            error = "Failed to send message",
                        )
                    }
                }

                is Result.Loading -> { /* no-op */ }
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
