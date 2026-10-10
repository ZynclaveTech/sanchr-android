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

    /**
     * Several photos or videos sent as one message, as iOS sends them.
     *
     * Received albums used to be decoded with `attachments.first()` and the
     * rest discarded: an album of four photos from iOS arrived on Android as
     * one photo, and the other three were unrecoverable from the UI.
     */
    @Serializable
    data class Album(
        val attachments: List<MediaAttachment>,
        val caption: String? = null,
    ) : MessageContent {
        init {
            require(attachments.isNotEmpty()) { "an album needs at least one attachment" }
        }

        /** The tile the bubble leads with, and the one a preview falls back to. */
        val first: MediaAttachment get() = attachments.first()

        companion object {
            const val CONTENT_TYPE = "album"
        }
    }

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

    /**
     * A local tombstone or notice, never sent (iOS `.system`). [kind] is the
     * stored body, e.g. [VIEW_ONCE_CONSUMED] once view-once media was opened.
     */
    @Serializable
    data class System(
        val kind: String,
    ) : MessageContent {
        val text: String
            get() =
                when (kind) {
                    VIEW_ONCE_CONSUMED -> "Viewed"
                    AUTO_VAULTED -> "Media moved to Vault"
                    else -> kind
                }

        companion object {
            const val CONTENT_TYPE = "system"
            const val VIEW_ONCE_CONSUMED = "viewOnceConsumed"

            /** Left where received media was after Auto-Vault Incoming moved it (iOS `.autoVaulted`). */
            const val AUTO_VAULTED = "autoVaulted"
        }
    }
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
