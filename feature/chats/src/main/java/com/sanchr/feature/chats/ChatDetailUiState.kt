package com.sanchr.feature.chats

import com.sanchr.core.model.Conversation
import com.sanchr.core.model.Message

/**
 * UI state for the chat detail (conversation) screen.
 */
data class ChatDetailUiState(
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val currentUserId: String = "",
    val messageInput: String = "",
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val errorMessage: String? = null,
    val isTyping: Boolean = false, // Remote user is typing
)
