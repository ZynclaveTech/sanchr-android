package com.sanchr.feature.chats

import com.sanchr.core.model.Conversation

/**
 * UI state for the chat detail (conversation) screen.
 */
data class ChatDetailUiState(
    val conversation: Conversation? = null,
    val messages: List<MessageUiModel> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val peerTyping: Boolean = false,
    val isPeerOnline: Boolean = false,
    val peerPresenceText: String? = null,
    val peerPresenceHidden: Boolean = false,
    val error: String? = null,
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val currentUserId: String = "",
)

data class MessageUiModel(
    val id: String,
    val text: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val status: MessageStatus,
    val contentType: String,
)

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED,
}
