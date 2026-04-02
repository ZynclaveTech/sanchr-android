package com.sanchr.feature.chats

import com.sanchr.core.crypto.DecryptedMessage
import com.sanchr.core.crypto.SignalKeyManager
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.proto.keys.GetUserDevicesRequest
import com.sanchr.proto.keys.KeyServiceClient
import com.sanchr.proto.messaging.DeviceMessage
import com.sanchr.proto.messaging.EncryptedEnvelope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between the chat ViewModel layer and the core crypto layer.
 *
 * Handles the orchestration logic that the ViewModel should not own:
 * - Fetching device lists for multi-device encryption
 * - Ensuring sessions are established before encrypting
 * - Converting between crypto-layer types and proto transport types
 * - Pre-key replenishment checks after decryption
 *
 * This keeps the ViewModel thin and the crypto module transport-agnostic.
 */
@Singleton
class ChatEncryptionHelper @Inject constructor(
    private val sessionManager: SignalSessionManager,
    private val keyManager: SignalKeyManager,
    private val keyServiceClient: KeyServiceClient,
) {

    /**
     * Encrypts a plaintext message for all devices of every recipient.
     *
     * For each recipient:
     * 1. Fetches their active device list from the key server.
     * 2. Ensures a Signal session exists with each device (establishes via X3DH if not).
     * 3. Encrypts the plaintext separately for each device.
     *
     * @param plaintext The UTF-8 message text to encrypt.
     * @param recipientIds List of recipient user IDs.
     * @return List of [DeviceMessage] proto objects ready for [SendMessageRequest].
     */
    suspend fun encryptMessage(
        plaintext: String,
        recipientIds: List<String>,
    ): List<DeviceMessage> {
        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        val deviceMessages = mutableListOf<DeviceMessage>()

        for (recipientId in recipientIds) {
            val devices = keyServiceClient.getUserDevices(
                GetUserDevicesRequest(userId = recipientId),
            ).devices

            for (device in devices) {
                val deviceIdInt = device.deviceId.toIntOrNull() ?: 1

                // Ensure session exists
                if (!sessionManager.hasSession(recipientId, deviceIdInt)) {
                    sessionManager.establishSession(recipientId, deviceIdInt)
                }

                val encryptResult = sessionManager.encrypt(
                    plaintext = plaintextBytes,
                    userId = recipientId,
                    deviceId = deviceIdInt,
                )

                deviceMessages.add(
                    DeviceMessage(
                        deviceId = device.deviceId,
                        registrationId = device.registrationId,
                        cipherText = encryptResult.ciphertext,
                        messageType = encryptResult.messageType,
                    ),
                )
            }
        }

        return deviceMessages
    }

    /**
     * Decrypts an incoming [EncryptedEnvelope] from the server.
     *
     * @param envelope The encrypted envelope received via the message stream.
     * @return A [DecryptedMessage] containing the plaintext and metadata.
     */
    suspend fun decryptEnvelope(envelope: EncryptedEnvelope): DecryptedMessage {
        return sessionManager.decryptEnvelope(envelope)
    }

    /**
     * Ensures Signal sessions are established with all devices of the given users.
     * Call this proactively (e.g., when opening a conversation) to avoid latency
     * on the first message send.
     *
     * @param recipientIds List of user IDs to pre-establish sessions with.
     */
    suspend fun ensureSessionsEstablished(recipientIds: List<String>) {
        for (recipientId in recipientIds) {
            val devices = keyServiceClient.getUserDevices(
                GetUserDevicesRequest(userId = recipientId),
            ).devices

            for (device in devices) {
                val deviceIdInt = device.deviceId.toIntOrNull() ?: 1
                if (!sessionManager.hasSession(recipientId, deviceIdInt)) {
                    sessionManager.establishSession(recipientId, deviceIdInt)
                }
            }
        }
    }

    /**
     * Checks and replenishes pre-keys if the server count is low.
     * Should be called after processing incoming messages, as each
     * PreKeySignalMessage consumes one one-time pre-key.
     */
    suspend fun checkPreKeyReplenishment() {
        keyManager.checkAndReplenishPreKeys()
    }
}
