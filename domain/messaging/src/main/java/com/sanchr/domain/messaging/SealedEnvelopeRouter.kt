package com.sanchr.domain.messaging

/**
 * What an inbound decrypted plaintext turned out to be, once
 * [SealedEnvelopeRouter.route] has looked at it.
 */
sealed interface RoutedPayload {
    /**
     * A message body to persist and show to the user.
     *
     * [conversationId] and (`""` when absent) [messageId] (`null` when
     * absent) are the payload's own ids — populated only when the
     * plaintext decoded as an [InnerPayload] and it set them. The sender
     * puts a stable id in `InnerPayload.messageId` so that a resend of the
     * same logical message replaces the same local row instead of
     * duplicating it (see [ReceiveMessageUseCase]); a bare legacy plaintext
     * carries no such id, so callers must fall back to the envelope's own
     * ids in that case.
     */
    data class UserMessage(
        val content: String,
        val contentType: String,
        val conversationId: String = "",
        val messageId: String? = null,
        /**
         * The sender's disappearing-message timer, in seconds, or null when
         * the sender set none. Travels inside the envelope rather than the
         * request so the server never learns it. Null for a legacy bare-text
         * plaintext, which has nowhere to carry it.
         */
        val expiresAfterSecs: Long? = null,
        /**
         * The sender's Profile Key (`sender_profile_key`), which every peer
         * rides on every payload so that anyone who can read their messages
         * can read their profile. Null for a legacy bare-text plaintext.
         */
        val senderProfileKey: ByteArray? = null,
    ) : RoutedPayload {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is UserMessage) return false
            return content == other.content &&
                contentType == other.contentType &&
                conversationId == other.conversationId &&
                messageId == other.messageId &&
                expiresAfterSecs == other.expiresAfterSecs &&
                (senderProfileKey?.contentEquals(other.senderProfileKey) ?: (other.senderProfileKey == null))
        }

        override fun hashCode(): Int {
            var result = content.hashCode()
            result = 31 * result + contentType.hashCode()
            result = 31 * result + conversationId.hashCode()
            result = 31 * result + (messageId?.hashCode() ?: 0)
            result = 31 * result + (expiresAfterSecs?.hashCode() ?: 0)
            result = 31 * result + (senderProfileKey?.contentHashCode() ?: 0)
            return result
        }
    }

    /**
     * A control payload — e.g. `receipt/v1`, `profile-key/v1` — that must
     * never be persisted as a visible message. [payload] is the full
     * decoded [InnerPayload] for a future plan to act on.
     */
    data class Control(
        val contentType: String,
        val payload: InnerPayload,
    ) : RoutedPayload

    /**
     * Reserved for a future rule that needs to drop a payload outright.
     * No rule produces this today — an inner payload with a content type
     * this build does not recognise is still routed to [UserMessage] so a
     * future content type never silently disappears; see [route].
     */
    data object Ignored : RoutedPayload
}

/**
 * Decides what an inbound decrypted plaintext is, ahead of persistence.
 *
 * A pure function — no database, no cipher — so [ReceiveMessageUseCase]
 * can act on its result without either.
 */
object SealedEnvelopeRouter {
    /** Content types that are control traffic, never a visible message. */
    private val CONTROL_CONTENT_TYPES = setOf("receipt/v1", "profile-key/v1")

    /**
     * @param plaintext The decrypted envelope bytes.
     * @param fallbackContentType The envelope's own content type. Used verbatim
     *   when [plaintext] is not an [InnerPayload] — the bare UTF-8 text older
     *   Android builds send.
     */
    fun route(
        plaintext: ByteArray,
        fallbackContentType: String,
    ): RoutedPayload {
        // Gate on the same "a JSON object carrying a v key" check iOS uses
        // before attempting a full decode. InnerPayload.decode alone is not
        // enough: it defaults every field, so a legacy plaintext that
        // happens to be valid JSON with no v key (even "{}") would
        // otherwise decode into an empty-content payload instead of being
        // treated as legacy text.
        if (!InnerPayload.isInnerPayload(plaintext)) {
            return RoutedPayload.UserMessage(
                content = String(plaintext, Charsets.UTF_8),
                contentType = fallbackContentType,
            )
        }
        val payload =
            InnerPayload.decode(plaintext) ?: return RoutedPayload.UserMessage(
                content = String(plaintext, Charsets.UTF_8),
                contentType = fallbackContentType,
            )

        return if (payload.contentType in CONTROL_CONTENT_TYPES) {
            RoutedPayload.Control(contentType = payload.contentType, payload = payload)
        } else {
            RoutedPayload.UserMessage(
                content = String(payload.content, Charsets.UTF_8),
                contentType = payload.contentType,
                conversationId = payload.conversationId,
                messageId = payload.messageId,
                // Treat a non-positive timer as "no timer": the wire field is
                // a plain int, so 0 is what a sender with the feature off
                // emits, and a negative value is nonsense we must not turn
                // into an already-past deadline.
                expiresAfterSecs = payload.expiresAfterSecs?.takeIf { it > 0 },
                senderProfileKey = payload.senderProfileKey,
            )
        }
    }
}
