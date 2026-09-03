package com.sanchr.domain.messaging

/**
 * Closed outcome surface of [ReceiveMessageUseCase]. Every inbound envelope
 * — sealed or non-sealed — funnels through that use case and yields exactly
 * one of these variants, so the caller (FCM drain worker, SyncWorker,
 * RealtimeManager) can switch on the result without repeating libsignal
 * exception-classification logic.
 */
sealed interface EnvelopeDecryptResult {
    data class Success(
        val senderUserId: String,
        val senderDeviceId: Int,
        val plaintext: ByteArray,
        val serverTimestamp: Long,
    ) : EnvelopeDecryptResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return senderUserId == other.senderUserId &&
                senderDeviceId == other.senderDeviceId &&
                plaintext.contentEquals(other.plaintext) &&
                serverTimestamp == other.serverTimestamp
        }

        override fun hashCode(): Int {
            var r = senderUserId.hashCode()
            r = 31 * r + senderDeviceId
            r = 31 * r + plaintext.contentHashCode()
            r = 31 * r + serverTimestamp.hashCode()
            return r
        }
    }

    /**
     * Envelope landed in `quarantined_envelopes`; caller MUST NOT retry on
     * its own — the user-surfaced quarantine table is the recovery path.
     */
    data class Quarantined(
        val reason: String,
        val failureClass: FailureClass,
    ) : EnvelopeDecryptResult

    /**
     * Peer session is absent / broken; the use case has scheduled a
     * best-effort session rebuild. Caller should drop this envelope on
     * the floor — the next message from this peer (post-rebuild) will
     * succeed, or the envelope will surface as quarantined on retry.
     */
    data object SessionMissing : EnvelopeDecryptResult

    /**
     * libsignal detected a replay / already-delivered counter. No write,
     * no quarantine — caller acks and moves on.
     */
    data object DuplicateMessage : EnvelopeDecryptResult
}

/**
 * Canonical failure taxonomy stored in `quarantined_envelopes.failure_class`.
 * Kept deliberately small — UI / ops tooling in M5 will key off these names.
 */
enum class FailureClass {
    INVALID_MESSAGE,
    DUPLICATE,
    SESSION_MISSING,
    CRYPTO_OTHER,

    /**
     * Outbound-only: [SendMessageUseCase] observed a conversation with no
     * reachable recipients (either the recipient set resolved empty, or the
     * per-recipient device fan-out produced zero `DeviceMessage` rows). Used
     * to surface a terminal FAILED status on the outbound message so the UI
     * can render a distinct failure affordance instead of silently reporting
     * SENT.
     */
    NO_RECIPIENTS,

    /**
     * Outbound or inbound: libsignal reported an `UntrustedIdentityException`
     * / `ProtocolUntrustedIdentityException`. The peer's identity key has
     * rotated since the session was established. Requires explicit user
     * action on a safety-number screen (M6) to re-trust; retrying blindly
     * will fail the same way.
     */
    UNTRUSTED_IDENTITY,

    /**
     * Outbound-only: [SendMessageUseCase] observed an encryption fan-out
     * of more than 100 device messages for one sealed send — the server
     * rejects a `SendSealedMessage` call over that limit with
     * `INVALID_ARGUMENT`, and consumes the delivery token before it
     * validates, so retrying the same oversized fan-out would burn a
     * fresh token every attempt. The row is marked terminally FAILED
     * before a token is ever acquired.
     */
    TOO_MANY_RECIPIENTS,
}
