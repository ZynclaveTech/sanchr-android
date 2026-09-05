package com.sanchr.core.callengine.signaling

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The plaintext that gets Signal-encrypted into `CallOffer.device_offers[].encrypted_sdp_payload`
 * and `CallSignal.encrypted_sdp_answer`. Field names are iOS's
 * (`SealedCallPayload.swift`): the two clients must decode each other.
 */
@Serializable
data class SealedCallPayload(
    /** The full SDP, offer or answer. */
    val sdp: String,
    /** "offer" or "answer". Older payloads omit it and are answers in stream signaling. */
    val type: String? = null,
    /** `a=fingerprint:` value from [sdp], e.g. "sha-256 AA:BB:…". Bound to the ciphertext so a relay cannot swap the SDP. */
    @SerialName("dtls_fingerprint") val dtlsFingerprint: String,
    /** Unix seconds at creation (iOS `TimeInterval`), for replay detection. */
    val timestamp: Double,
) {
    fun encode(): ByteArray = JSON.encodeToString(this).toByteArray(Charsets.UTF_8)

    companion object {
        private val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = false
            }

        fun decode(bytes: ByteArray): SealedCallPayload = JSON.decodeFromString(serializer(), String(bytes, Charsets.UTF_8))

        /**
         * The DTLS fingerprint an SDP advertises, or null when it has none —
         * same extraction as iOS `WebRTCClient.extractDtlsFingerprint`.
         */
        fun fingerprintOf(sdp: String): String? =
            sdp
                .lineSequence()
                .firstOrNull { it.startsWith(FINGERPRINT_PREFIX) }
                ?.removePrefix(FINGERPRINT_PREFIX)
                ?.trim()

        private const val FINGERPRINT_PREFIX = "a=fingerprint:"
    }
}
