package com.sanchr.feature.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.common.Result
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.SendMessageUseCase
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
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    private val _uiState = MutableStateFlow(ChatDetailUiState())
    val uiState: StateFlow<ChatDetailUiState> = _uiState.asStateFlow()

    init {
        observeMessages()
        markAsRead()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            messageRepository.observeMessages(conversationId).collect { messages ->
                _uiState.update {
                    it.copy(
                        messages = messages,
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

    fun onMessageInputChanged(text: String) {
        _uiState.update { it.copy(messageInput = text) }
        // TODO: Send typing indicator via gRPC
    }

    fun sendMessage() {
        val content = _uiState.value.messageInput.trim()
        if (content.isBlank()) return

        _uiState.update { it.copy(messageInput = "", isSending = true) }

        viewModelScope.launch {
            when (val result = sendMessageUseCase(conversationId, content)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSending = false) }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = "Failed to send message",
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
            // TODO: Use oldest message timestamp for pagination
            val olderMessages = messageRepository.loadMoreMessages(
                conversationId = conversationId,
                beforeTimestamp = 0L, // TODO: Use actual timestamp
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
        _uiState.update { it.copy(errorMessage = null) }
    }
}
