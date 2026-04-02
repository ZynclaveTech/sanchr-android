package com.sanchr.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: String,
    val type: ConversationType,
    val participants: List<User>,
    val title: String? = null,
    val avatarUrl: String? = null,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val disappearingMessageDuration: Long? = null,
    val updatedAt: Instant,
    val createdAt: Instant,
)

@Serializable
enum class ConversationType {
    DIRECT,
    GROUP,
}
