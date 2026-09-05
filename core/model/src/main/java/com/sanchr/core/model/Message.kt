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
    /**
     * Taxonomy name for why [status] is `FAILED` — stored as a plain string
     * in `core:model` to avoid a dependency from `core:model` onto
     * `domain:messaging`. UI layer maps the known names
     * (`NO_RECIPIENTS`, `UNTRUSTED_IDENTITY`, `CRYPTO_OTHER`, ...) to
     * distinct affordances; null while the row is in any non-terminal
     * state.
     */
    val failureClass: String? = null,
    /**
     * Human-readable failure reason surfaced to the UI as a tooltip /
     * long-press detail on the failed-message affordance. Null while the
     * row is in any non-terminal state.
     */
    val failureReason: String? = null,
    /** Emoji reactions on this message, oldest first. */
    val reactions: List<MessageReaction> = emptyList(),
)

/** One user's emoji on a message (iOS `Message.MessageReaction`). */
@Serializable
data class MessageReaction(
    val emoji: String,
    val userId: String,
    val timestamp: Instant,
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
        /** The encrypted object behind [url]; null for legacy unencrypted links. */
        val attachment: MediaAttachment? = null,
    ) : MessageContent

    @Serializable
    data class Voice(
        val url: String,
        val durationMs: Long,
        val waveform: List<Float> = emptyList(),
        val attachment: MediaAttachment? = null,
    ) : MessageContent

    @Serializable
    data class File(
        val url: String,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val attachment: MediaAttachment? = null,
    ) : MessageContent

    @Serializable
    data class Location(
        val latitude: Double,
        val longitude: Double,
        val label: String? = null,
    ) : MessageContent

    /** A shared contact card (`contact` content type, see [ContactCard]). */
    @Serializable
    data class Contact(
        val name: String,
        val phoneNumber: String,
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
