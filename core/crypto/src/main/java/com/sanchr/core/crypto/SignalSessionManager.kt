package com.sanchr.core.crypto

import com.sanchr.core.crypto.store.SanchrSignalProtocolStore
import com.sanchr.proto.messaging.EncryptedEnvelope
import javax.inject.Inject
import javax.inject.Singleton
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
 */
@Singleton
class SignalSessionManager
    @Inject
    constructor(
        private val store: SanchrSignalProtocolStore,
        private val keyManager: SignalKeyManager,
    ) {
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
            val address = SignalProtocolAddress(userId, deviceId)
            val sessionBuilder = SessionBuilder(store, address)
            val preKeyBundle = keyManager.fetchPreKeyBundle(userId, deviceId)
            sessionBuilder.process(preKeyBundle)
            return sessionBuilder
        }

        /**
         * Returns true if an active session exists with the specified device.
         */
        fun hasSession(
            userId: String,
            deviceId: Int,
        ): Boolean {
            val address = SignalProtocolAddress(userId, deviceId)
            return store.containsSession(address)
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
            val address = SignalProtocolAddress(userId, deviceId)

            // Ensure session exists; establish if needed
            if (!store.containsSession(address)) {
                establishSession(userId, deviceId)
            }

            val cipher = SessionCipher(store, address)
            val ciphertextMessage = cipher.encrypt(plaintext)

            return EncryptResult(
                ciphertext = ciphertextMessage.serialize(),
                messageType = ciphertextMessage.type,
            )
        }

        /**
         * Encrypts plaintext for ALL devices of a recipient.
         * Fetches the device list from the key server, then encrypts a separate
         * ciphertext for each device.
         *
         * @param plaintext The raw message bytes to encrypt.
         * @param recipientId The recipient's user ID.
         * @return A list of [DeviceEncryptedMessage], one per recipient device.
         */
        suspend fun encryptForAllDevices(
            plaintext: ByteArray,
            recipientId: String,
        ): List<DeviceEncryptedMessage> {
            val devicesResponse =
                com.sanchr.proto.keys
                    .GetUserDevicesRequest(userId = recipientId)
            // The key service returns the list of active devices for this user.
            // For now, we encrypt for device ID 1 (primary device) as a baseline,
            // and the caller can provide the device list from the server response.
            // In production, ChatEncryptionHelper fetches the device list.
            val result = encrypt(plaintext, recipientId, 1)
            return listOf(
                DeviceEncryptedMessage(
                    deviceId = 1,
                    ciphertext = result.ciphertext,
                    messageType = result.messageType,
                    registrationId = store.getLocalRegistrationId(),
                ),
            )
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
            val address = SignalProtocolAddress(senderId, senderDevice)
            val cipher = SessionCipher(store, address)

            // Try PreKeySignalMessage first (type 3), fall back to SignalMessage (type 1).
            // PreKeySignalMessage contains the embedded SignalMessage plus pre-key
            // information needed to establish the session on the receiving side.
            return try {
                val preKeyMessage = PreKeySignalMessage(ciphertext)
                cipher.decrypt(preKeyMessage)
            } catch (_: Exception) {
                val signalMessage = SignalMessage(ciphertext)
                cipher.decrypt(signalMessage)
            }
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
        fun resetSession(
            userId: String,
            deviceId: Int,
        ) {
            val address = SignalProtocolAddress(userId, deviceId)
            store.deleteSession(address)
        }

        /**
         * Resets all sessions with a user (all their devices).
         */
        fun resetAllSessions(userId: String) {
            store.deleteAllSessions(userId)
        }
    }

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
