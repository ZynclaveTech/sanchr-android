package com.sanchr.core.crypto.sealed

import android.util.Base64
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.BuildConfig
import com.sanchr.core.crypto.MessagePadding
import com.sanchr.core.crypto.store.SanchrSignalProtocolStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import org.signal.libsignal.metadata.SealedSessionCipher
import org.signal.libsignal.metadata.certificate.CertificateValidator
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey

/**
 * Supplies the sealed-sender TrustRoot public key used to validate server-issued
 * [org.signal.libsignal.metadata.certificate.SenderCertificate]s on decrypt.
 *
 * Implementations must either return a valid public key or throw. Callers MUST
 * NOT attempt to decrypt sealed-sender envelopes without a configured TrustRoot.
 */
fun interface TrustRootProvider {
    fun trustRoot(): ECPublicKey
}

/**
 * Default [TrustRootProvider] backed by `BuildConfig.SEALED_SENDER_TRUST_ROOT`
 * — a Base64-encoded libsignal `ECPublicKey` serialization.
 *
 * M2 ships with the field empty; once the backend publishes the TrustRoot
 * (planned M3 per the crypto plan), the value is baked in at build time.
 */
@Singleton
class BuildConfigTrustRootProvider
    @Inject
    constructor() : TrustRootProvider {
        override fun trustRoot(): ECPublicKey {
            val raw = BuildConfig.SEALED_SENDER_TRUST_ROOT
            check(raw.isNotEmpty()) {
                "TrustRoot not configured — wire in M3 (BuildConfig.SEALED_SENDER_TRUST_ROOT is empty)"
            }
            val bytes = Base64.decode(raw, Base64.NO_WRAP or Base64.NO_PADDING)
            return ECPublicKey(bytes)
        }
    }

/**
 * Sealed-sender encrypt/decrypt for 1:1 messages (per Signal sealed-sender spec
 * + app spec §4). Hides the sender identity from the transport server: the
 * server sees only the recipient; the sender's identity is inside the envelope,
 * signed by a [SenderCertificate][org.signal.libsignal.metadata.certificate.SenderCertificate]
 * that the receiver validates against the [TrustRootProvider].
 *
 * All libsignal calls are serialized on [DispatcherProvider.signalDispatcher],
 * matching the thread-safety contract the rest of the crypto module relies on.
 */
@Singleton
class SealedSenderCipher
    @Inject
    constructor(
        private val store: SanchrSignalProtocolStore,
        private val certManager: SenderCertificateManager,
        private val trustRootProvider: TrustRootProvider,
        private val dispatchers: DispatcherProvider,
    ) {
        /**
         * Sealed-sender encrypt for a single recipient device. Requires an
         * existing Signal session (establish via `SignalSessionManager` first).
         *
         * @return the unidentified-sender envelope bytes ready for transport.
         */
        suspend fun sealedEncrypt(
            recipient: SignalProtocolAddress,
            plaintext: ByteArray,
        ): ByteArray {
            val certificate = certManager.current()
            return withContext(dispatchers.signalDispatcher) {
                val cipher =
                    SealedSessionCipher(
                        store,
                        store.localUserUuid,
                        null, // localE164 — we never publish E.164 in sealed envelopes
                        store.localDeviceId,
                    )
                cipher.encrypt(recipient, certificate, plaintext)
            }
        }

        /**
         * Sealed-sender decrypt. Validates the embedded [SenderCertificate]
         * against the configured TrustRoot at the supplied `timestamp`
         * (typically the server-stamped receive time).
         *
         * @throws IllegalStateException if the TrustRoot is not configured.
         */
        suspend fun sealedDecrypt(
            envelope: ByteArray,
            timestamp: Long,
        ): DecryptedEnvelope {
            val validator = CertificateValidator(trustRootProvider.trustRoot())
            return withContext(dispatchers.signalDispatcher) {
                val cipher =
                    SealedSessionCipher(
                        store,
                        store.localUserUuid,
                        null, // localE164 — we never publish E.164 in sealed envelopes
                        store.localDeviceId,
                    )
                val result: SealedSessionCipher.DecryptionResult =
                    cipher.decrypt(validator, envelope, timestamp)
                DecryptedEnvelope(
                    senderUserId = result.senderUuid,
                    senderDeviceId = result.deviceId,
                    plaintext = MessagePadding.strip(result.paddedMessage),
                )
            }
        }

        /**
         * Result of a successful sealed-sender decrypt.
         *
         * `plaintext` has already been unpadded via [MessagePadding.strip]
         * (libsignal 0x80 sentinel scheme) — consumers get raw application
         * bytes, no further processing required.
         */
        data class DecryptedEnvelope(
            val senderUserId: String,
            val senderDeviceId: Int,
            val plaintext: ByteArray,
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is DecryptedEnvelope) return false
                return senderUserId == other.senderUserId &&
                    senderDeviceId == other.senderDeviceId &&
                    plaintext.contentEquals(other.plaintext)
            }

            override fun hashCode(): Int {
                var r = senderUserId.hashCode()
                r = 31 * r + senderDeviceId
                r = 31 * r + plaintext.contentHashCode()
                return r
            }
        }
    }
