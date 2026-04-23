package com.sanchr.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Envelope(
    val envelopeId: String,
    val receivedAt: Instant,
    val payload: ByteArray,
    val attempts: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Envelope) return false
        return envelopeId == other.envelopeId &&
            receivedAt == other.receivedAt &&
            payload.contentEquals(other.payload) &&
            attempts == other.attempts
    }

    override fun hashCode(): Int {
        var r = envelopeId.hashCode()
        r = 31 * r + receivedAt.hashCode()
        r = 31 * r + payload.contentHashCode()
        r = 31 * r + attempts
        return r
    }
}
