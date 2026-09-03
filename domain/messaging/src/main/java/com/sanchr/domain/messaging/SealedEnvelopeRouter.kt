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
    ) : RoutedPayload

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
            )
        }
    }
}
