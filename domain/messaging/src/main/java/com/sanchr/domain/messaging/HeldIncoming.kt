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
)
