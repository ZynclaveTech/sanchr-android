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
import com.sanchr.sync.realtime.RealtimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val messageRepository: MessageRepository,
        private val sendMessageUseCase: SendMessageUseCase,
        private val sessionManager: SessionManager,
        private val realtimeManager: RealtimeManager,
        private val notificationHandler: NotificationHandler,
    ) : ViewModel() {
        private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

        private val _uiState =
            MutableStateFlow(
                ChatDetailUiState(
                    currentUserId = sessionManager.getUserId() ?: "",
                ),
            )
        val uiState: StateFlow<ChatDetailUiState> = _uiState.asStateFlow()

        init {
            observeConversation()
            observeMessages()
            observeTyping()
            markAsRead()
            clearNotificationsForConversation()
        }

        private fun observeConversation() {
            viewModelScope.launch {
                messageRepository.observeConversation(conversationId).collect { conversation ->
                    _uiState.update { state ->
                        state.copy(conversation = conversation)
                    }
                }
            }
        }

        private fun observeMessages() {
            viewModelScope.launch {
                messageRepository.observeMessages(conversationId).collect { messages ->
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

        private fun markAsRead() {
            viewModelScope.launch {
                messageRepository.markAsRead(conversationId)
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
            return MessageUiModel(
                id = id,
                text =
                    when (content) {
                        is MessageContent.Text -> (content as MessageContent.Text).body
                        is MessageContent.Image -> (content as MessageContent.Image).caption ?: "[Image]"
                        is MessageContent.Voice -> "[Voice message]"
                        is MessageContent.File -> (content as MessageContent.File).fileName
                        is MessageContent.Location -> (content as MessageContent.Location).label ?: "[Location]"
                    },
                timestamp = timestamp.toEpochMilliseconds(),
                isFromMe = senderId == currentUser,
                status =
                    when (status) {
                        com.sanchr.core.model.MessageStatus.QUEUED -> MessageStatus.SENDING
                        com.sanchr.core.model.MessageStatus.SENDING -> MessageStatus.SENDING
                        com.sanchr.core.model.MessageStatus.SENT -> MessageStatus.SENT
                        com.sanchr.core.model.MessageStatus.DELIVERED -> MessageStatus.DELIVERED
                        com.sanchr.core.model.MessageStatus.READ -> MessageStatus.READ
                        com.sanchr.core.model.MessageStatus.FAILED -> MessageStatus.FAILED
                    },
                contentType =
                    when (content) {
                        is MessageContent.Text -> "text"
                        is MessageContent.Image -> "image"
                        is MessageContent.Voice -> "voice"
                        is MessageContent.File -> "file"
                        is MessageContent.Location -> "location"
                    },
            )
        }
    }
