package com.sanchr.core.crypto

import com.sanchr.core.crypto.store.SanchrIdentityKeyStore
import com.sanchr.core.crypto.store.SanchrPreKeyStore
import com.sanchr.core.crypto.store.SanchrSignalProtocolStore
import com.sanchr.core.crypto.store.SanchrSignedPreKeyStore
import com.sanchr.proto.keys.GetPreKeyBundleRequest
import com.sanchr.proto.keys.GetPreKeyCountRequest
import com.sanchr.proto.keys.KeyBundle
import com.sanchr.proto.keys.KeyServiceClient
import com.sanchr.proto.keys.OneTimePreKey
import com.sanchr.proto.keys.UploadOneTimePreKeysRequest
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages all Signal Protocol key lifecycle operations:
 * - Identity key pair generation (once, at registration)
 * - Signed pre-key generation and monthly rotation
 * - One-time pre-key batch generation and server replenishment
 * - Pre-key bundle upload to and retrieval from the key server
 *
 * All generated keys are persisted through the protocol stores and uploaded
 * to the server via [KeyServiceClient] so that other devices can establish
 * X3DH sessions with this device.
 */
@Singleton
class SignalKeyManager @Inject constructor(
    private val store: SanchrSignalProtocolStore,
    private val identityKeyStore: SanchrIdentityKeyStore,
    private val preKeyStore: SanchrPreKeyStore,
    private val signedPreKeyStore: SanchrSignedPreKeyStore,
    private val keyServiceClient: KeyServiceClient,
) {

    companion object {
        /** Number of one-time pre-keys to generate per batch. */
        const val PRE_KEY_BATCH_SIZE = 100

        /** Server-side threshold below which we generate more pre-keys. */
        const val PRE_KEY_REPLENISH_THRESHOLD = 25

        /** Signed pre-keys are rotated every 30 days. */
        private val SIGNED_PRE_KEY_ROTATION_MILLIS = TimeUnit.DAYS.toMillis(30)
    }

    // ------------------------------------------------------------------
    // Key Generation
    // ------------------------------------------------------------------

    /**
     * Generates the local identity key pair. Called exactly once during registration.
     * The identity key pair is a long-lived Curve25519 key pair that represents
     * this device's cryptographic identity.
     *
     * @return The generated [IdentityKeyPair].
     */
    fun generateIdentity(): IdentityKeyPair {
        val identityKeyPair = IdentityKeyPair.generate()
        val registrationId = KeyHelper.generateRegistrationId(false)

        identityKeyStore.storeIdentityKeyPair(identityKeyPair)
        identityKeyStore.storeLocalRegistrationId(registrationId)

        return identityKeyPair
    }

    /**
     * Generates a signed pre-key, signed by the local identity key.
     * Signed pre-keys are medium-term keys rotated approximately monthly.
     *
     * @param identityKeyPair The identity key pair used to sign the pre-key.
     * @return The generated [SignedPreKeyRecord].
     */
    fun generateSignedPreKey(identityKeyPair: IdentityKeyPair): SignedPreKeyRecord {
        val signedPreKeyId = signedPreKeyStore.getNextSignedPreKeyId()
        val signedPreKeyPair = Curve.generateKeyPair()
        val signature = Curve.calculateSignature(
            identityKeyPair.privateKey,
            signedPreKeyPair.publicKey.serialize(),
        )
        val timestamp = System.currentTimeMillis()

        val signedPreKeyRecord = SignedPreKeyRecord(
            signedPreKeyId,
            timestamp,
            signedPreKeyPair,
            signature,
        )

        signedPreKeyStore.storeSignedPreKey(signedPreKeyId, signedPreKeyRecord)
        return signedPreKeyRecord
    }

    /**
     * Generates a batch of one-time pre-keys starting from [startId].
     * One-time pre-keys are consumed during X3DH session establishment and
     * provide forward secrecy for the initial message.
     *
     * @param startId The starting pre-key ID for this batch.
     * @param count Number of pre-keys to generate (defaults to [PRE_KEY_BATCH_SIZE]).
     * @return List of generated [PreKeyRecord]s.
     */
    fun generateOneTimePreKeys(
        startId: Int,
        count: Int = PRE_KEY_BATCH_SIZE,
    ): List<PreKeyRecord> {
        val records = mutableListOf<PreKeyRecord>()
        for (i in 0 until count) {
            val preKeyId = startId + i
            val keyPair = Curve.generateKeyPair()
            val record = PreKeyRecord(preKeyId, keyPair)
            preKeyStore.storePreKey(preKeyId, record)
            records.add(record)
        }
        return records
    }

    // ------------------------------------------------------------------
    // Server Sync
    // ------------------------------------------------------------------

    /**
     * Uploads the full initial key bundle to the server after registration.
     * This includes the identity public key, signed pre-key, and a batch
     * of one-time pre-keys.
     *
     * Must be called after [generateIdentity] has been called.
     */
    suspend fun uploadInitialKeyBundle() {
        val identityKeyPair = identityKeyStore.getIdentityKeyPair()
        val registrationId = identityKeyStore.getLocalRegistrationId()

        val signedPreKey = generateSignedPreKey(identityKeyPair)
        val oneTimePreKeys = generateOneTimePreKeys(
            startId = preKeyStore.getNextPreKeyId(),
        )

        val signedPreKeyProto = com.sanchr.proto.keys.SignedPreKey(
            keyId = signedPreKey.id,
            publicKey = signedPreKey.keyPair.publicKey.serialize(),
            signature = signedPreKey.signature,
            timestamp = signedPreKey.timestamp,
        )

        val oneTimePreKeyProtos = oneTimePreKeys.map { record ->
            OneTimePreKey(
                keyId = record.id,
                publicKey = record.keyPair.publicKey.serialize(),
            )
        }

        val bundle = KeyBundle(
            identityKey = identityKeyPair.publicKey.serialize(),
            signedPreKey = signedPreKeyProto,
            oneTimePreKeys = oneTimePreKeyProtos,
            registrationId = registrationId,
        )

        keyServiceClient.uploadKeyBundle(bundle)
    }

    /**
     * Generates and uploads additional one-time pre-keys to the server.
     * Called when the server signals that the pre-key count is low.
     */
    suspend fun replenishPreKeys() {
        val startId = preKeyStore.getNextPreKeyId()
        val newKeys = generateOneTimePreKeys(startId)

        val preKeyProtos = newKeys.map { record ->
            OneTimePreKey(
                keyId = record.id,
                publicKey = record.keyPair.publicKey.serialize(),
            )
        }

        keyServiceClient.uploadOneTimePreKeys(
            UploadOneTimePreKeysRequest(preKeys = preKeyProtos),
        )
    }

    /**
     * Checks the server-side pre-key count and replenishes if below threshold.
     * Should be called periodically (e.g., on app foreground or via WorkManager).
     */
    suspend fun checkAndReplenishPreKeys() {
        val response = keyServiceClient.getPreKeyCount(GetPreKeyCountRequest())
        if (response.count < PRE_KEY_REPLENISH_THRESHOLD) {
            replenishPreKeys()
        }
    }

    // ------------------------------------------------------------------
    // Pre-Key Bundle Retrieval
    // ------------------------------------------------------------------

    /**
     * Fetches a recipient's pre-key bundle from the server for X3DH session
     * establishment. The returned [PreKeyBundle] contains the recipient's
     * identity key, signed pre-key, and (optionally) a one-time pre-key.
     *
     * @param userId The recipient's user ID.
     * @param deviceId The recipient's device ID (as an integer).
     * @return A [PreKeyBundle] suitable for passing to [SessionBuilder.process].
     */
    suspend fun fetchPreKeyBundle(userId: String, deviceId: Int): PreKeyBundle {
        val response = keyServiceClient.getPreKeyBundle(
            GetPreKeyBundleRequest(
                userId = userId,
                deviceId = deviceId.toString(),
            ),
        )

        val identityKey = IdentityKey(response.identityKey)
        val signedPreKey = response.signedPreKey
            ?: throw IllegalStateException("Server returned no signed pre-key for $userId:$deviceId")

        val oneTimePreKey = response.oneTimePreKey

        return PreKeyBundle(
            response.registrationId,
            deviceId,
            oneTimePreKey?.keyId ?: 0,
            if (oneTimePreKey != null) Curve.decodePoint(oneTimePreKey.publicKey, 0) else null,
            signedPreKey.keyId,
            Curve.decodePoint(signedPreKey.publicKey, 0),
            signedPreKey.signature,
            identityKey,
        )
    }

    // ------------------------------------------------------------------
    // Signed Pre-Key Rotation
    // ------------------------------------------------------------------

    /**
     * Rotates the signed pre-key if the current one is older than 30 days.
     * The new signed pre-key is uploaded to the server as part of a fresh
     * key bundle. Old signed pre-keys are retained for a grace period to
     * allow in-flight messages to be decrypted.
     */
    suspend fun rotateSignedPreKeyIfNeeded() {
        val signedPreKeys = signedPreKeyStore.loadSignedPreKeys()
        if (signedPreKeys.isEmpty()) return

        val latest = signedPreKeys.maxByOrNull { it.timestamp } ?: return
        val age = System.currentTimeMillis() - latest.timestamp

        if (age < SIGNED_PRE_KEY_ROTATION_MILLIS) return

        val identityKeyPair = identityKeyStore.getIdentityKeyPair()
        val newSignedPreKey = generateSignedPreKey(identityKeyPair)

        // Upload updated bundle with new signed pre-key
        val signedPreKeyProto = com.sanchr.proto.keys.SignedPreKey(
            keyId = newSignedPreKey.id,
            publicKey = newSignedPreKey.keyPair.publicKey.serialize(),
            signature = newSignedPreKey.signature,
            timestamp = newSignedPreKey.timestamp,
        )

        val bundle = KeyBundle(
            identityKey = identityKeyPair.publicKey.serialize(),
            signedPreKey = signedPreKeyProto,
            registrationId = identityKeyStore.getLocalRegistrationId(),
        )

        keyServiceClient.uploadKeyBundle(bundle)

        // Clean up signed pre-keys older than 2 rotation periods (grace period)
        val gracePeriod = SIGNED_PRE_KEY_ROTATION_MILLIS * 2
        signedPreKeys
            .filter { System.currentTimeMillis() - it.timestamp > gracePeriod }
            .filter { it.id != newSignedPreKey.id }
            .forEach { signedPreKeyStore.removeSignedPreKey(it.id) }
    }

    /**
     * Returns true if the local identity key pair has been generated.
     */
    fun hasIdentity(): Boolean = identityKeyStore.hasIdentityKeyPair()
}
