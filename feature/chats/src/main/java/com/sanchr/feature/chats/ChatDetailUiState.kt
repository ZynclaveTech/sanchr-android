package com.sanchr.feature.chats

import com.sanchr.core.model.ContactCard
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.MediaAttachment

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
    /** "Online" / "Last seen …" from the peer's presence, or null. */
    val peerPresence: String? = null,
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
    /**
     * When [status] is [MessageStatus.FAILED], the kind of failure — drives
     * the icon + tooltip on the failed-message affordance. Null for
     * non-terminal rows and for FAILED rows whose taxonomy name is not
     * known to this client (treated as [FailureKind.GENERIC]).
     */
    val failureKind: FailureKind? = null,
    /**
     * Human-readable failure reason for the tooltip / long-press detail.
     * Null when [status] is not FAILED.
     */
    val failureReason: String? = null,
    /** The encrypted media behind an image/voice/file row; null for text. */
    val attachment: MediaAttachment? = null,
    /** The shared card behind a contact row; null otherwise. */
    val contact: ContactCard? = null,
)

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED,
}

/**
 * UI taxonomy of outbound-send failures. Maps 1:1 to the domain
 * `FailureClass` names that can surface on outbound rows; kept as a
 * separate enum in the feature module so `core:model` / UI do not depend
 * on `domain:messaging`.
 */
enum class FailureKind {
    /** Peer's identity key rotated — needs a safety-number decision (M6). */
    UNTRUSTED_IDENTITY,

    /** Anything else (no recipients, crypto error, server error, ...). */
    GENERIC,
}
