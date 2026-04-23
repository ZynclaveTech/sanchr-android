package com.sanchr.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: MessageContent,
    val status: MessageStatus,
    val timestamp: Instant,
    val editedAt: Instant? = null,
    val replyToId: String? = null,
    val expiresAt: Instant? = null,
)

@Serializable
sealed interface MessageContent {
    @Serializable
    data class Text(
        val body: String,
    ) : MessageContent

    @Serializable
    data class Image(
        val url: String,
        val thumbnailUrl: String?,
        val width: Int,
        val height: Int,
        val caption: String? = null,
    ) : MessageContent

    @Serializable
    data class Voice(
        val url: String,
        val durationMs: Long,
        val waveform: List<Float> = emptyList(),
    ) : MessageContent

    @Serializable
    data class File(
        val url: String,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
    ) : MessageContent

    @Serializable
    data class Location(
        val latitude: Double,
        val longitude: Double,
        val label: String? = null,
    ) : MessageContent
}

@Serializable
enum class MessageStatus {
    QUEUED,
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED,
}
