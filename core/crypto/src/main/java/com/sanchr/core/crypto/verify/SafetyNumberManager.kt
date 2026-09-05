package com.sanchr.core.crypto.verify

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.store.SanchrIdentityKeyStore
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator

/**
 * Safety numbers: the 60 digits two people read to each other, and the
 * scannable fingerprint behind the QR code, for one contact.
 *
 * Both come from a single [NumericFingerprintGenerator] run over the same
 * inputs as iOS `SignalProtocol.safetyNumber` — version 2, 5200 iterations,
 * the user ids as the identifiers — so the two platforms compute the same
 * number for the same pair and a cross-platform scan matches.
 *
 * There is deliberately no placeholder when the crypto cannot run. A screen
 * that invents digits shows the same constant on both devices, the codes
 * appear to match, and the user marks a session verified that was never
 * checked. Failing visibly is the only safe behaviour, so every entry point
 * throws rather than substituting anything.
 */
@Singleton
class SafetyNumberManager
    @Inject
    constructor(
        private val identityKeyStore: SanchrIdentityKeyStore,
        private val dispatchers: DispatcherProvider,
    ) {
        /**
         * The digits and the QR payload for [userId], plus when the local user
         * last verified them.
         *
         * @throws IllegalStateException if no identity key for the contact is
         *   known yet, which means no message has been exchanged.
         */
        suspend fun safetyNumber(
            userId: String,
            deviceId: Int = DEFAULT_DEVICE_ID,
        ): SafetyNumber =
            withContext(dispatchers.default) {
                val address = SignalProtocolAddress(userId, deviceId)
                val fingerprint = fingerprintFor(userId, address)
                SafetyNumber(
                    digits = fingerprint.displayableFingerprint.displayText,
                    scannablePayload = fingerprint.scannableFingerprint.serialized,
                    verifiedAtMillis = identityKeyStore.verifiedAtMillis(address),
                )
            }

        /**
         * Whether [scanned] is the fingerprint of this same pair of identities.
         *
         * A scan of an unrelated or corrupt code returns [ScanOutcome.Unreadable]
         * rather than false: "not a Sanchr code" and "a Sanchr code that does not
         * match" mean very different things to someone checking for a wiretap.
         */
        suspend fun compare(
            userId: String,
            scanned: ByteArray,
            deviceId: Int = DEFAULT_DEVICE_ID,
        ): ScanOutcome =
            withContext(dispatchers.default) {
                val address = SignalProtocolAddress(userId, deviceId)
                val fingerprint = fingerprintFor(userId, address)
                try {
                    if (fingerprint.scannableFingerprint.compareTo(scanned)) {
                        ScanOutcome.Match
                    } else {
                        ScanOutcome.Mismatch
                    }
                } catch (_: Exception) {
                    // Both libsignal failure modes — a version this build does not
                    // speak, and bytes that are not a fingerprint at all — mean the
                    // same thing to the user: that code told us nothing.
                    ScanOutcome.Unreadable
                }
            }

        /** Records that the codes were compared and matched. */
        suspend fun markVerified(
            userId: String,
            deviceId: Int = DEFAULT_DEVICE_ID,
            atMillis: Long = System.currentTimeMillis(),
        ) = withContext(dispatchers.io) {
            identityKeyStore.markVerified(SignalProtocolAddress(userId, deviceId), atMillis)
        }

        /** Revokes a verification, after a mismatch or at the user's request. */
        suspend fun clearVerified(
            userId: String,
            deviceId: Int = DEFAULT_DEVICE_ID,
        ) = withContext(dispatchers.io) {
            identityKeyStore.clearVerified(SignalProtocolAddress(userId, deviceId))
        }

        /**
         * A fingerprint of this device's own identity public key, in twelve
         * six-character blocks.
         *
         * Account-level and one-sided, so it is not a safety number and cannot
         * verify anything on its own — it is what the Encryption Keys screen
         * shows so the digits there are at least real. Comparing two people's
         * keys is [safetyNumber]'s job.
         */
        suspend fun localIdentityFingerprintBlocks(): List<String> =
            withContext(dispatchers.default) {
                val digest = MessageDigest.getInstance("SHA-256").digest(identityKeyStore.getIdentityKeyPair().publicKey.serialize())
                digest
                    .joinToString("") { "%02x".format(it) }
                    .take(FINGERPRINT_BLOCKS * FINGERPRINT_BLOCK_CHARS)
                    .chunked(FINGERPRINT_BLOCK_CHARS)
            }

        private fun fingerprintFor(
            userId: String,
            address: SignalProtocolAddress,
        ) = NumericFingerprintGenerator(ITERATIONS).createFor(
            VERSION,
            identityKeyStore.getLocalUserUuid().toString().toByteArray(Charsets.UTF_8),
            identityKeyStore.getIdentityKeyPair().publicKey,
            userId.toByteArray(Charsets.UTF_8),
            identityKeyStore.getIdentity(address)
                ?: throw IllegalStateException("No identity key for $userId — exchange a message first"),
        )

        private companion object {
            /** Matches iOS `NumericFingerprintGenerator(iterations: 5200)`. */
            const val ITERATIONS = 5200
            const val VERSION = 2
            const val DEFAULT_DEVICE_ID = 1
            const val FINGERPRINT_BLOCKS = 12
            const val FINGERPRINT_BLOCK_CHARS = 6
        }
    }

/** One contact's safety number, ready to show. */
data class SafetyNumber(
    /** The 60 digits, as libsignal groups them into twelve blocks of five. */
    val digits: String,
    /** The bytes the QR code carries, and that a scan is compared against. */
    val scannablePayload: ByteArray,
    /** When the local user last marked this identity verified, or null. */
    val verifiedAtMillis: Long?,
) {
    /**
     * The digits in twelve groups of five, the layout both apps print them in.
     * Falls back to whole-string chunking if libsignal ever stops inserting
     * spaces, so the screen degrades to unspaced digits rather than blank.
     */
    val digitGroups: List<String>
        get() = digits.split(" ").filter { it.isNotBlank() }.ifEmpty { digits.chunked(GROUP_SIZE) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SafetyNumber) return false
        return digits == other.digits &&
            scannablePayload.contentEquals(other.scannablePayload) &&
            verifiedAtMillis == other.verifiedAtMillis
    }

    override fun hashCode(): Int {
        var r = digits.hashCode()
        r = 31 * r + scannablePayload.contentHashCode()
        r = 31 * r + verifiedAtMillis.hashCode()
        return r
    }

    private companion object {
        const val GROUP_SIZE = 5
    }
}

/** What a scanned QR code told us about this conversation. */
enum class ScanOutcome {
    /** The codes are for the same pair of identities: safe to mark verified. */
    Match,

    /** A valid fingerprint for a different pair: someone is between you. */
    Mismatch,

    /** Not a fingerprint this build can read, so it proves nothing either way. */
    Unreadable,
}
