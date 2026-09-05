package com.sanchr.core.crypto

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.sealed.SealedSenderCipher
import com.sanchr.core.crypto.sealed.SenderCertificateManager
import com.sanchr.core.crypto.store.SanchrSignalProtocolStore
import com.sanchr.proto.keys.GetUserDevicesRequest
import com.sanchr.proto.keys.KeyServiceClient
import com.sanchr.proto.messaging.EncryptedEnvelope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage

/**
 * Manages Signal Protocol sessions for end-to-end encrypted messaging.
 *
 * Responsibilities:
 * - X3DH session establishment with remote devices via [SessionBuilder]
 * - Double Ratchet message encryption via [SessionCipher]
 * - Automatic detection and handling of PreKeySignalMessage vs SignalMessage
 * - Multi-device encryption (encrypt once per recipient device)
 * - Session lifecycle (creation, existence checks, reset/recovery)
 *
 * All operations are backed by [SanchrSignalProtocolStore] which persists
 * session state, identity keys, and pre-keys to encrypted local storage.
 *
 * All libsignal-touching operations are serialized on [DispatcherProvider.signalDispatcher]
 * (a single-threaded dispatcher) to guarantee thread-safety of the underlying
 * native libsignal state across concurrent encrypt/decrypt/establish calls.
 */
@Singleton
class SignalSessionManager
    @Inject
    constructor(
        private val store: SanchrSignalProtocolStore,
        private val keyManager: SignalKeyManager,
        private val dispatchers: DispatcherProvider,
        private val sealedSenderCipher: SealedSenderCipher,
        private val senderCertificateManager: SenderCertificateManager,
        private val keyServiceClient: KeyServiceClient,
    ) {
        private companion object {
            const val TAG = "SignalSessionManager"
        }
        // ------------------------------------------------------------------
        // Sealed-sender (delegates to SealedSenderCipher — see that class for
        // the full contract). Kept in SignalSessionManager so callers have a
        // single entry point for all session-scoped crypto.
        // ------------------------------------------------------------------

        /** Sealed-sender encrypt for a single recipient device. */
        suspend fun encryptSealed(
            recipient: SignalProtocolAddress,
            plaintext: ByteArray,
        ): ByteArray = sealedSenderCipher.sealedEncrypt(recipient, MessagePadding.pad(plaintext))

        /** Sealed-sender decrypt. `timestamp` is the server receive time. */
        suspend fun decryptSealed(
            envelope: ByteArray,
            timestamp: Long,
        ): SealedSenderCipher.DecryptedEnvelope = sealedSenderCipher.sealedDecrypt(envelope, timestamp)

        // ------------------------------------------------------------------
        // Session Establishment
        // ------------------------------------------------------------------

        /**
         * Establishes a new Signal session with a recipient's device using X3DH.
         * Fetches the recipient's pre-key bundle from the server and processes it
         * to create a session.
         *
         * This must be called before encrypting the first message to a new device.
         * Subsequent messages use the existing session and the Double Ratchet.
         *
         * @param userId The recipient's user ID.
         * @param deviceId The recipient's device ID.
         * @return The [SessionBuilder] used to establish the session.
         */
        suspend fun establishSession(
            userId: String,
            deviceId: Int,
        ): SessionBuilder {
            val preKeyBundle = keyManager.fetchPreKeyBundle(userId, deviceId)
            return withContext(dispatchers.signalDispatcher) {
                val address = SignalProtocolAddress(userId, deviceId)
                val sessionBuilder = SessionBuilder(store, address)
                sessionBuilder.process(preKeyBundle)
                sessionBuilder
            }
        }

        /**
         * Returns true if an active session exists with the specified device.
         */
        suspend fun hasSession(
            userId: String,
            deviceId: Int,
        ): Boolean =
            withContext(dispatchers.signalDispatcher) {
                val address = SignalProtocolAddress(userId, deviceId)
                store.containsSession(address)
            }

        // ------------------------------------------------------------------
        // Encryption
        // ------------------------------------------------------------------

        /**
         * Encrypts plaintext for a specific recipient device.
         *
         * Uses [SessionCipher] which implements the Double Ratchet algorithm.
         * The returned bytes are a serialized [CiphertextMessage] that can be
         * one of two types:
         * - [PreKeySignalMessage] (type 3): first message in a new session
         * - [SignalMessage] (type 1): subsequent messages in an established session
         *
         * @param plaintext The raw message bytes to encrypt.
         * @param userId The recipient's user ID.
         * @param deviceId The recipient's device ID.
         * @return Serialized ciphertext bytes ready for transport.
         */
        suspend fun encrypt(
            plaintext: ByteArray,
            userId: String,
            deviceId: Int,
        ): EncryptResult {
            // Session establishment (if needed) must happen outside the signalDispatcher
            // block because it performs a network call via keyManager.fetchPreKeyBundle.
            val sessionExists =
                withContext(dispatchers.signalDispatcher) {
                    store.containsSession(SignalProtocolAddress(userId, deviceId))
                }
            if (!sessionExists) {
                establishSession(userId, deviceId)
            }

            val padded = MessagePadding.pad(plaintext)
            return withContext(dispatchers.signalDispatcher) {
                val address = SignalProtocolAddress(userId, deviceId)
                val cipher = SessionCipher(store, address)
                val ciphertextMessage = cipher.encrypt(padded)

                EncryptResult(
                    ciphertext = ciphertextMessage.serialize(),
                    messageType = ciphertextMessage.type,
                )
            }
        }

        /**
         * Encrypts plaintext for ALL key-capable devices of a recipient.
         *
         * 1. Fetches the recipient's active device list via `KeyService.GetUserDevices`.
         * 2. For each device, ensures a Signal session exists (running X3DH if needed).
         * 3. Encrypts via the sealed-sender path. There is no non-sealed
         *    fallback — v1 is sealed-only on outbound per the M3 scope, so a
         *    missing/expired sender certificate must propagate as an error.
         *
         * A per-device failure (stale pre-key, invalid key material, missing
         * session, untrusted identity) is logged and skipped so that delivery
         * to other devices still succeeds. Global failures — notably a missing
         * or unfetchable sender certificate — propagate to the caller so the
         * outbound state machine can requeue instead of shipping an empty
         * device list.
         *
         * @param plaintext The raw message bytes to encrypt.
         * @param recipientId The recipient's user ID.
         * @return A list of [DeviceEncryptedMessage], one entry per recipient device
         *         for which encryption succeeded. Never empty when `devices` is
         *         non-empty: if every per-device encrypt fails this throws
         *         [EncryptFanOutEmptyException].
         *
         * @throws EncryptFanOutEmptyException when at least one key-capable
         *   device was discovered but every per-device encrypt failed. Treated
         *   as a transient send failure by [com.sanchr.domain.messaging.SendMessageUseCase].
         */
        suspend fun encryptForAllDevices(
            plaintext: ByteArray,
            recipientId: String,
        ): List<DeviceEncryptedMessage> {
            val devices =
                keyServiceClient
                    .getUserDevices(GetUserDevicesRequest(userId = recipientId))
                    .devices
                    .filter { it.keyCapable }

            if (devices.isEmpty()) {
                Log.w(TAG, "No key-capable devices for recipient $recipientId")
                return emptyList()
            }

            // Prime the sender-certificate cache BEFORE the per-device loop.
            // SealedSenderCipher.sealedEncrypt calls SenderCertificateManager.current()
            // internally on every invocation. If the cert is missing and the
            // refresh RPC is down, every device iteration would fail the same
            // way and the old broad catch (t: Throwable) would swallow each
            // one, returning an empty list that the send path then shipped to
            // the server — dropping the message. Hoisting the fetch here lets
            // a cert-unavailable failure propagate instead of degrading into
            // silent dataloss.
            senderCertificateManager.current()

            val registrationId =
                withContext(dispatchers.signalDispatcher) {
                    store.getLocalRegistrationId()
                }

            val results = ArrayList<DeviceEncryptedMessage>(devices.size)
            for (device in devices) {
                val deviceId = device.deviceId
                try {
                    if (!hasSession(recipientId, deviceId)) {
                        establishSession(recipientId, deviceId)
                    }

                    val address = SignalProtocolAddress(recipientId, deviceId)
                    val sealedCiphertext =
                        sealedSenderCipher.sealedEncrypt(address, MessagePadding.pad(plaintext))
                    results.add(
                        DeviceEncryptedMessage(
                            deviceId = deviceId,
                            ciphertext = sealedCiphertext,
                            // Sealed-sender envelopes are a distinct transport type;
                            // type 0 signals "not a plain CiphertextMessage".
                            messageType = 0,
                            registrationId = registrationId,
                        ),
                    )
                } catch (t: InvalidKeyException) {
                    // Per-device: stale / malformed prekey bundle. Other devices
                    // remain deliverable.
                    Log.w(TAG, "Failed to encrypt for $recipientId:$deviceId — skipping", t)
                } catch (t: NoSessionException) {
                    // Per-device: session disappeared between hasSession() and
                    // the encrypt call (concurrent reset, store corruption).
                    Log.w(TAG, "Failed to encrypt for $recipientId:$deviceId — skipping", t)
                }
                // UntrustedIdentityException is deliberately NOT caught here:
                // it signals that the peer's identity key has rotated, which
                // is a user-facing trust decision (safety-number screen in
                // M6) — retrying blindly will fail the same way. Propagate
                // so SendMessageUseCase can mark the outbound row FAILED
                // with FailureClass.UNTRUSTED_IDENTITY and surface a
                // distinct UI affordance rather than silently dropping the
                // message on this recipient while happily sending to
                // others.
                // Any other exception (I/O, sender-cert failure, cancellation,
                // programmer error) propagates so the caller can make an
                // informed decision rather than silently dropping the fanout.
            }
            if (results.isEmpty()) {
                // devices was non-empty (checked above) and every per-device
                // encrypt failed. Surface this instead of returning an empty
                // list so SendMessageUseCase does not ship an empty fan-out to
                // the server.
                throw EncryptFanOutEmptyException(recipientId, devices.size)
            }
            return results
        }

        // ------------------------------------------------------------------
        // Decryption
        // ------------------------------------------------------------------

        /**
         * Decrypts incoming ciphertext from a specific sender device.
         *
         * Auto-detects the message type:
         * - [PreKeySignalMessage] (type 3): first message establishing a new session.
         *   The session is created as a side effect of decryption.
         * - [SignalMessage] (type 1): subsequent message in an established session.
         *
         * @param ciphertext The serialized ciphertext bytes.
         * @param senderId The sender's user ID.
         * @param senderDevice The sender's device ID.
         * @return The decrypted plaintext bytes.
         */
        suspend fun decrypt(
            ciphertext: ByteArray,
            senderId: String,
            senderDevice: Int,
        ): ByteArray {
            val padded =
                withContext(dispatchers.signalDispatcher) {
                    val address = SignalProtocolAddress(senderId, senderDevice)
                    val cipher = SessionCipher(store, address)

                    // Try PreKeySignalMessage first (type 3), fall back to SignalMessage (type 1).
                    // PreKeySignalMessage contains the embedded SignalMessage plus pre-key
                    // information needed to establish the session on the receiving side.
                    try {
                        val preKeyMessage = PreKeySignalMessage(ciphertext)
                        cipher.decrypt(preKeyMessage)
                    } catch (_: Exception) {
                        val signalMessage = SignalMessage(ciphertext)
                        cipher.decrypt(signalMessage)
                    }
                }
            return MessagePadding.strip(padded)
        }

        /**
         * Decrypts a full [EncryptedEnvelope] received from the server.
         *
         * The envelope contains the sender identity, conversation context,
         * and the encrypted ciphertext. This method extracts the ciphertext,
         * decrypts it, and returns a [DecryptedMessage] with full metadata.
         *
         * @param envelope The encrypted envelope from the server.
         * @return A [DecryptedMessage] containing plaintext and metadata.
         */
        suspend fun decryptEnvelope(envelope: EncryptedEnvelope): DecryptedMessage {
            val senderDeviceId = envelope.senderDevice.takeIf { it > 0 } ?: 1
            val plaintext =
                decrypt(
                    ciphertext = envelope.cipherText,
                    senderId = envelope.senderId,
                    senderDevice = senderDeviceId,
                )

            return DecryptedMessage(
                conversationId = envelope.conversationId,
                messageId = envelope.messageId,
                senderId = envelope.senderId,
                senderDevice = senderDeviceId,
                plaintext = plaintext,
                contentType = envelope.contentType,
                serverTimestamp = envelope.serverTimestamp,
            )
        }

        // ------------------------------------------------------------------
        // Session Management
        // ------------------------------------------------------------------

        // ------------------------------------------------------------------
        // Call payloads (SDP offers / answers)
        // ------------------------------------------------------------------

        /**
         * Encrypts a call payload for one device the way iOS does: no padding,
         * and the ciphertext framed with a leading type byte
         * ([CallCiphertextFraming]). Establishes a session first if none.
         */
        suspend fun encryptForCall(
            plaintext: ByteArray,
            userId: String,
            deviceId: Int,
        ): ByteArray {
            if (!hasSession(userId, deviceId)) establishSession(userId, deviceId)
            return withContext(dispatchers.signalDispatcher) {
                val cipher = SessionCipher(store, SignalProtocolAddress(userId, deviceId))
                CallCiphertextFraming.frame(cipher.encrypt(plaintext))
            }
        }

        /** Inverse of [encryptForCall]; dispatches on the type byte instead of parsing speculatively. */
        suspend fun decryptForCall(
            framed: ByteArray,
            senderId: String,
            senderDevice: Int,
        ): ByteArray =
            withContext(dispatchers.signalDispatcher) {
                val (type, body) = CallCiphertextFraming.unframe(framed)
                val cipher = SessionCipher(store, SignalProtocolAddress(senderId, senderDevice))
                if (type == CallCiphertextFraming.PREKEY) {
                    cipher.decrypt(PreKeySignalMessage(body))
                } else {
                    cipher.decrypt(SignalMessage(body))
                }
            }

        /**
         * One ciphertext per key-capable device of [recipientId], for
         * `CallOffer.device_offers`. Mirrors iOS `encryptCallOffers`: the
         * session with each device is reset first so the offer is always a
         * fresh PreKeySignalMessage — a stale ratchet the callee can no
         * longer decrypt would otherwise kill the call, and the next
         * message on the new session heals messaging too.
         *
         * A device that cannot be encrypted for is skipped and logged; if
         * none can be, this throws so the caller does not place a call
         * nobody can answer.
         */
        suspend fun encryptCallOffers(
            plaintext: ByteArray,
            recipientId: String,
        ): List<DeviceCallCiphertext> {
            val devices =
                keyServiceClient
                    .getUserDevices(GetUserDevicesRequest(userId = recipientId))
                    .devices
                    .filter { it.keyCapable }
            val results =
                devices.mapNotNull { device ->
                    try {
                        resetSession(recipientId, device.deviceId)
                        DeviceCallCiphertext(device.deviceId, encryptForCall(plaintext, recipientId, device.deviceId))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "call offer encrypt failed for device ${device.deviceId}", e)
                        null
                    }
                }
            if (results.isEmpty()) throw EncryptFanOutEmptyException(recipientId, devices.size)
            return results
        }

        /**
         * Resets/deletes the session with a specific device.
         * Used for session recovery when decryption fails persistently,
         * indicating a corrupted or out-of-sync session.
         *
         * After reset, the next encrypt call will re-establish the session
         * via X3DH automatically.
         *
         * @param userId The remote user ID.
         * @param deviceId The remote device ID.
         */
        suspend fun resetSession(
            userId: String,
            deviceId: Int,
        ) = withContext(dispatchers.signalDispatcher) {
            val address = SignalProtocolAddress(userId, deviceId)
            store.deleteSession(address)
        }

        /**
         * Resets all sessions with a user (all their devices).
         */
        suspend fun resetAllSessions(userId: String) =
            withContext(dispatchers.signalDispatcher) {
                store.deleteAllSessions(userId)
            }
    }

/** One `CallOffer.device_offers` entry: an iOS-framed call ciphertext for a device. */
class DeviceCallCiphertext(
    val deviceId: Int,
    val ciphertext: ByteArray,
)

/**
 * Result of encrypting plaintext for a single device.
 */

data class EncryptResult(
    /** Serialized ciphertext bytes. */
    val ciphertext: ByteArray,
    /** CiphertextMessage type: 1 = SignalMessage, 3 = PreKeySignalMessage. */
    val messageType: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptResult) return false
        return ciphertext.contentEquals(other.ciphertext) && messageType == other.messageType
    }

    override fun hashCode(): Int = ciphertext.contentHashCode() * 31 + messageType
}

/**
 * Encrypted message payload for a single recipient device.
 * A list of these is sent to the server as part of [SendMessageRequest].
 */
data class DeviceEncryptedMessage(
    /** The target device ID. */
    val deviceId: Int,
    /** Serialized ciphertext for this specific device. */
    val ciphertext: ByteArray,
    /** CiphertextMessage type (1 or 3). */
    val messageType: Int,
    /** Local registration ID for the sender. */
    val registrationId: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceEncryptedMessage) return false
        return deviceId == other.deviceId && ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int = deviceId * 31 + ciphertext.contentHashCode()
}

/**
 * Thrown by [SignalSessionManager.encryptForAllDevices] when at least one
 * key-capable device was discovered but every per-device encrypt attempt
 * failed. Treated as a transient send failure by
 * [com.sanchr.domain.messaging.SendMessageUseCase] — the outbound row stays
 * QUEUED and `SendRetryWorker` will re-attempt.
 *
 * The send path MUST NOT ship a `SendMessage` RPC with an empty device list:
 * the server treats that as a successful send and the message is lost.
 */
class EncryptFanOutEmptyException(
    val recipientId: String,
    val deviceCount: Int,
) : RuntimeException(
        "All $deviceCount device encrypt attempts for recipient $recipientId failed",
    )

/**
 * The result of decrypting an [EncryptedEnvelope] -- contains the plaintext
 * along with all envelope metadata needed to store and display the message.
 */
data class DecryptedMessage(
    val conversationId: String,
    val messageId: String,
    val senderId: String,
    val senderDevice: Int,
    val plaintext: ByteArray,
    val contentType: String,
    val serverTimestamp: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecryptedMessage) return false
        return messageId == other.messageId && senderId == other.senderId
    }

    override fun hashCode(): Int = messageId.hashCode()
}
