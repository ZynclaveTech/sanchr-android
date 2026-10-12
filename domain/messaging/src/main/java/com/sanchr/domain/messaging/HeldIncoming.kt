package com.sanchr.domain.messaging

/**
 * A received message that decrypted but could not be filed, kept so it can
 * be filed later. See [HeldIncomingRetrier].
 */
class HeldIncoming(
    /** 0 until it has been stored. */
    val id: Long = 0,
    val decrypted: EnvelopeDecryptResult.Success,
    /** The envelope's own ids and type: what the server knows it by. */
    val envelope: IncomingEnvelopeContext,
    val attempts: Int = 0,
    /** Epoch millis; 0 until it has been stored. */
    val heldAtMillis: Long = 0,
) {
    /**
     * Whether this is no longer worth filing, and must not be kept either.
     *
     * Holding a message keeps what it said. A disappearing message whose
     * time is up must not sit here past it — filed now, it would be removed
     * at once anyway — and nothing is held longer than [MAX_AGE_MILLIS],
     * whatever it is.
     */
    fun isSpent(nowMillis: Long): Boolean {
        if (heldAtMillis > 0 && nowMillis - heldAtMillis >= MAX_AGE_MILLIS) return true
        val lifetimeSecs = InnerPayload.decode(decrypted.plaintext)?.expiresAfterSecs?.takeIf { it > 0 } ?: return false
        return decrypted.serverTimestamp + lifetimeSecs * MILLIS_PER_SECOND <= nowMillis
    }

    companion object {
        /** As long as the server itself would have kept an undelivered message. */
        const val MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000
        private const val MILLIS_PER_SECOND = 1000L
    }
}
