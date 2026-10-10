package com.sanchr.core.crypto

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.store.SanchrIdentityKeyStore
import com.sanchr.core.crypto.store.SanchrKyberPreKeyStore
import com.sanchr.core.crypto.store.SanchrPreKeyStore
import com.sanchr.core.crypto.store.SanchrSignalProtocolStore
import com.sanchr.core.crypto.store.SanchrSignedPreKeyStore
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.keys.GetPreKeyBundleRequest
import com.sanchr.proto.keys.GetPreKeyCountRequest
import com.sanchr.proto.keys.IdentityKeyLookupUnsupported
import com.sanchr.proto.keys.KeyBundle
import com.sanchr.proto.keys.KeyServiceClient
import com.sanchr.proto.keys.KyberPreKey
import com.sanchr.proto.keys.OneTimePreKey
import com.sanchr.proto.keys.UploadOneTimePreKeysRequest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper

/**
 * Manages all Signal Protocol key lifecycle operations:
 * - Identity key pair generation (once, at registration)
 * - Signed pre-key generation and weekly rotation
 * - One-time pre-key batch generation and server replenishment
 * - Pre-key bundle upload to and retrieval from the key server
 *
 * All generated keys are persisted through the protocol stores and uploaded
 * to the server via [KeyServiceClient] so that other devices can establish
 * PQXDH sessions with this device.
 *
 * All libsignal-touching operations are serialized on [DispatcherProvider.signalDispatcher]
 * (a single-threaded dispatcher) to guarantee thread-safety of the underlying
 * native libsignal state.
 */
@Singleton
class SignalKeyManager
    @Inject
    constructor(
        private val store: SanchrSignalProtocolStore,
        private val identityKeyStore: SanchrIdentityKeyStore,
        private val preKeyStore: SanchrPreKeyStore,
        private val signedPreKeyStore: SanchrSignedPreKeyStore,
        private val kyberPreKeyStore: SanchrKyberPreKeyStore,
        private val sessionManager: SessionManager,
        private val keyServiceClient: KeyServiceClient,
        private val dispatchers: DispatcherProvider,
    ) {
        companion object {
            /** Number of one-time pre-keys to generate per batch. */
            const val PRE_KEY_BATCH_SIZE = 100

            /** Server-side threshold below which we generate more pre-keys (per spec §4 M2). */
            const val PRE_KEY_REPLENISH_THRESHOLD = 20

            /** Signed pre-keys are rotated every 7 days (per spec §4 M2). */
            private val SIGNED_PRE_KEY_ROTATION_MILLIS = TimeUnit.DAYS.toMillis(7)
            private const val KYBER_ID_MASK = 0x00FF_FFFFL
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
        suspend fun generateIdentity(): IdentityKeyPair =
            withContext(dispatchers.signalDispatcher) {
                val identityKeyPair = IdentityKeyPair.generate()
                val registrationId = KeyHelper.generateRegistrationId(false)

                identityKeyStore.storeIdentityKeyPair(identityKeyPair)
                identityKeyStore.storeLocalRegistrationId(registrationId)

                identityKeyPair
            }

        /**
         * Generates a signed pre-key, signed by the local identity key.
         * Signed pre-keys are medium-term keys rotated approximately weekly.
         *
         * @param identityKeyPair The identity key pair used to sign the pre-key.
         * @return The generated [SignedPreKeyRecord].
         */
        suspend fun generateSignedPreKey(identityKeyPair: IdentityKeyPair): SignedPreKeyRecord =
            withContext(dispatchers.signalDispatcher) {
                generateSignedPreKeyBlocking(identityKeyPair)
            }

        private fun generateSignedPreKeyBlocking(identityKeyPair: IdentityKeyPair): SignedPreKeyRecord {
            val signedPreKeyId = signedPreKeyStore.getNextSignedPreKeyId()
            val signedPreKeyPair = ECKeyPair.generate()
            val signature =
                identityKeyPair.privateKey.calculateSignature(
                    signedPreKeyPair.publicKey.serialize(),
                )
            val timestamp = System.currentTimeMillis()

            val signedPreKeyRecord =
                SignedPreKeyRecord(
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
        suspend fun generateOneTimePreKeys(
            startId: Int,
            count: Int = PRE_KEY_BATCH_SIZE,
        ): List<PreKeyRecord> =
            withContext(dispatchers.signalDispatcher) {
                generateOneTimePreKeysBlocking(startId, count)
            }

        private fun generateOneTimePreKeysBlocking(
            startId: Int,
            count: Int,
        ): List<PreKeyRecord> {
            val records = mutableListOf<PreKeyRecord>()
            for (i in 0 until count) {
                val preKeyId = startId + i
                val keyPair = ECKeyPair.generate()
                val record = PreKeyRecord(preKeyId, keyPair)
                preKeyStore.storePreKey(preKeyId, record)
                records.add(record)
            }
            return records
        }

        suspend fun generateKyberPreKey(identityKeyPair: IdentityKeyPair): KyberPreKeyRecord =
            withContext(dispatchers.signalDispatcher) {
                generateKyberPreKeyBlocking(identityKeyPair)
            }

        private fun generateKyberPreKeyBlocking(identityKeyPair: IdentityKeyPair): KyberPreKeyRecord {
            // The low bits of the clock come round again every few hours, and
            // storing under an id already in use would overwrite the key held
            // there — one a peer may be about to send a first message to.
            var kyberPreKeyId = (System.currentTimeMillis() and KYBER_ID_MASK).toInt()
            while (kyberPreKeyId == 0 || kyberPreKeyStore.containsKyberPreKey(kyberPreKeyId)) {
                kyberPreKeyId = ((kyberPreKeyId + 1).toLong() and KYBER_ID_MASK).toInt()
            }
            val keyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
            val signature =
                identityKeyPair.privateKey.calculateSignature(
                    keyPair.publicKey.serialize(),
                )
            val timestamp = System.currentTimeMillis()

            val record =
                KyberPreKeyRecord(
                    kyberPreKeyId,
                    timestamp,
                    keyPair,
                    signature,
                )

            kyberPreKeyStore.storeKyberPreKey(kyberPreKeyId, record)
            return record
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
            val bundle =
                withContext(dispatchers.signalDispatcher) {
                    val identityKeyPair = identityKeyStore.getIdentityKeyPair()
                    val registrationId = identityKeyStore.getLocalRegistrationId()

                    val signedPreKey = generateSignedPreKeyBlocking(identityKeyPair)
                    val kyberPreKey = generateKyberPreKeyBlocking(identityKeyPair)
                    val oneTimePreKeys =
                        generateOneTimePreKeysBlocking(
                            startId = preKeyStore.getNextPreKeyId(),
                            count = PRE_KEY_BATCH_SIZE,
                        )

                    val signedPreKeyProto =
                        com.sanchr.proto.keys.SignedPreKey(
                            keyId = signedPreKey.id,
                            publicKey = signedPreKey.keyPair.publicKey.serialize(),
                            signature = signedPreKey.signature,
                            timestamp = signedPreKey.timestamp,
                        )

                    val oneTimePreKeyProtos =
                        oneTimePreKeys.map { record ->
                            OneTimePreKey(
                                keyId = record.id,
                                publicKey = record.keyPair.publicKey.serialize(),
                            )
                        }

                    KeyBundle(
                        identityPublicKey = identityKeyPair.publicKey.serialize(),
                        signedPreKey = signedPreKeyProto,
                        oneTimePreKeys = oneTimePreKeyProtos,
                        registrationId = registrationId,
                        deviceId = sessionManager.getDeviceId()?.toIntOrNull() ?: 0,
                        kyberPreKey =
                            KyberPreKey(
                                keyId = kyberPreKey.id,
                                publicKey = kyberPreKey.keyPair.publicKey.serialize(),
                                signature = kyberPreKey.signature,
                                timestamp = kyberPreKey.timestamp,
                            ),
                    )
                }

            keyServiceClient.uploadKeyBundle(bundle)
        }

        /**
         * Generates and uploads additional one-time pre-keys to the server.
         * Called when the server signals that the pre-key count is low.
         */
        suspend fun replenishPreKeys() {
            val preKeyProtos =
                withContext(dispatchers.signalDispatcher) {
                    val startId = preKeyStore.getNextPreKeyId()
                    val newKeys = generateOneTimePreKeysBlocking(startId, PRE_KEY_BATCH_SIZE)

                    newKeys.map { record ->
                        OneTimePreKey(
                            keyId = record.id,
                            publicKey = record.keyPair.publicKey.serialize(),
                        )
                    }
                }

            keyServiceClient.uploadOneTimePreKeys(
                UploadOneTimePreKeysRequest(keys = preKeyProtos),
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
         * The identity key the server publishes for a user's device, or null
         * when it publishes none.
         *
         * Asked with the call made for it, which costs the peer nothing. A
         * server that predates that call is asked for a whole bundle instead,
         * which spends one of the peer's one-time pre-keys — the only way
         * there was before.
         */
        suspend fun fetchPublishedIdentityKey(
            userId: String,
            deviceId: Int,
        ): IdentityKey? =
            try {
                keyServiceClient.getIdentityKey(userId, deviceId)?.let { IdentityKey(it) }
            } catch (e: IdentityKeyLookupUnsupported) {
                fetchPreKeyBundle(userId, deviceId).identityKey
            }

        /**
         * Fetches a recipient's pre-key bundle from the server for PQXDH session
         * establishment. The returned [PreKeyBundle] contains the recipient's
         * identity key, signed pre-key, (optionally) a one-time pre-key, and
         * a Kyber post-quantum pre-key.
         *
         * @param userId The recipient's user ID.
         * @param deviceId The recipient's device ID (as an integer).
         * @return A [PreKeyBundle] suitable for passing to [SessionBuilder.process].
         */
        suspend fun fetchPreKeyBundle(
            userId: String,
            deviceId: Int,
        ): PreKeyBundle {
            val response =
                keyServiceClient.getPreKeyBundle(
                    GetPreKeyBundleRequest(
                        userId = userId,
                        deviceId = deviceId,
                    ),
                )

            return withContext(dispatchers.signalDispatcher) {
                val identityKey = IdentityKey(response.identityPublicKey)
                val signedPreKey =
                    response.signedPreKey
                        ?: throw IllegalStateException("Server returned no signed pre-key for $userId:$deviceId")

                val oneTimePreKey = response.oneTimePreKey
                val kyberPreKey =
                    response.kyberPreKey
                        ?: throw IllegalStateException("Server returned no Kyber pre-key for $userId:$deviceId")

                PreKeyBundle(
                    response.registrationId,
                    deviceId,
                    oneTimePreKey?.keyId ?: PreKeyBundle.NULL_PRE_KEY_ID,
                    if (oneTimePreKey != null) ECPublicKey(oneTimePreKey.publicKey) else null,
                    signedPreKey.keyId,
                    ECPublicKey(signedPreKey.publicKey),
                    signedPreKey.signature,
                    identityKey,
                    kyberPreKey.keyId,
                    KEMPublicKey(kyberPreKey.publicKey),
                    kyberPreKey.signature,
                )
            }
        }

        // ------------------------------------------------------------------
        // Signed Pre-Key Rotation
        // ------------------------------------------------------------------

        /**
         * Replaces the signed pre-key and the Kyber pre-key once the pair the
         * server is handing out is older than 7 days.
         *
         * The order of operations is what makes this safe to interrupt:
         *
         *  - The new pair is stored before it is uploaded. From the moment
         *    the server has it a peer may send a first message to it.
         *  - The pair is remembered as pending until the upload is known to
         *    have succeeded, and a retry sends that same pair. An upload whose
         *    reply was lost may have reached the server; making another pair
         *    would leave the server handing out keys this device then lets
         *    go.
         *  - The clock runs from the newest pair that is *not* pending. It
         *    used to run from the newest stored, so a pair whose upload
         *    failed counted as a rotation and nothing was tried again for a
         *    week, with the server still handing out the old one.
         *  - Replaced keys are held for a grace period, for peers who fetched
         *    a bundle before the rotation, and then removed. Kyber pre-keys
         *    never were, so every one ever published was kept.
         */
        suspend fun rotateSignedPreKeyIfNeeded() {
            val prepared =
                withContext(dispatchers.signalDispatcher) {
                    val signedPreKeys = signedPreKeyStore.loadSignedPreKeys()
                    if (signedPreKeys.isEmpty()) return@withContext null

                    val pending = pendingRotationPair()
                    val published = signedPreKeys.filter { it.id != pending?.first?.id }
                    val latest = published.maxByOrNull { it.timestamp } ?: return@withContext null
                    val age = System.currentTimeMillis() - latest.timestamp

                    if (age < SIGNED_PRE_KEY_ROTATION_MILLIS) return@withContext null

                    val identityKeyPair = identityKeyStore.getIdentityKeyPair()
                    val (newSignedPreKey, newKyberPreKey) =
                        pending ?: (generateSignedPreKeyBlocking(identityKeyPair) to generateKyberPreKeyBlocking(identityKeyPair))
                            .also { kyberPreKeyStore.pendingRotation = it.first.id to it.second.id }

                    val signedPreKeyProto =
                        com.sanchr.proto.keys.SignedPreKey(
                            keyId = newSignedPreKey.id,
                            publicKey = newSignedPreKey.keyPair.publicKey.serialize(),
                            signature = newSignedPreKey.signature,
                            timestamp = newSignedPreKey.timestamp,
                        )

                    val bundle =
                        KeyBundle(
                            identityPublicKey = identityKeyPair.publicKey.serialize(),
                            signedPreKey = signedPreKeyProto,
                            registrationId = identityKeyStore.getLocalRegistrationId(),
                            deviceId = sessionManager.getDeviceId()?.toIntOrNull() ?: 0,
                            kyberPreKey =
                                KyberPreKey(
                                    keyId = newKyberPreKey.id,
                                    publicKey = newKyberPreKey.keyPair.publicKey.serialize(),
                                    signature = newKyberPreKey.signature,
                                    timestamp = newKyberPreKey.timestamp,
                                ),
                        )

                    RotationPrep(bundle, newSignedPreKey.id, newKyberPreKey.id, signedPreKeys)
                } ?: return

            keyServiceClient.uploadKeyBundle(prepared.bundle)

            withContext(dispatchers.signalDispatcher) {
                kyberPreKeyStore.pendingRotation = null
                // Clean up keys older than 2 rotation periods (grace period)
                val cutoff = System.currentTimeMillis() - SIGNED_PRE_KEY_ROTATION_MILLIS * 2
                prepared.existingSignedPreKeys
                    .filter { it.timestamp < cutoff }
                    .filter { it.id != prepared.newSignedPreKeyId }
                    .forEach { signedPreKeyStore.removeSignedPreKey(it.id) }
                kyberPreKeyStore
                    .loadKyberPreKeys()
                    .filter { it.timestamp < cutoff }
                    .filter { it.id != prepared.newKyberPreKeyId }
                    .forEach { kyberPreKeyStore.removeKyberPreKey(it.id) }
            }
        }

        /** The pair an earlier attempt stored, if both halves are still here. */
        private fun pendingRotationPair(): Pair<SignedPreKeyRecord, KyberPreKeyRecord>? {
            val (signedId, kyberId) = kyberPreKeyStore.pendingRotation ?: return null
            return runCatching { signedPreKeyStore.loadSignedPreKey(signedId) to kyberPreKeyStore.loadKyberPreKey(kyberId) }.getOrNull()
        }

        private data class RotationPrep(
            val bundle: KeyBundle,
            val newSignedPreKeyId: Int,
            val newKyberPreKeyId: Int,
            val existingSignedPreKeys: List<SignedPreKeyRecord>,
        )

        /**
         * Returns true if the local identity key pair has been generated.
         */
        fun hasIdentity(): Boolean = identityKeyStore.hasIdentityKeyPair()

        suspend fun hasCompleteServerBundle(
            userId: String,
            deviceId: Int,
        ): Boolean =
            keyServiceClient
                .getUserDevices(
                    com.sanchr.proto.keys
                        .GetUserDevicesRequest(userId = userId),
                ).devices
                .any { it.deviceId == deviceId && it.keyCapable }
    }
