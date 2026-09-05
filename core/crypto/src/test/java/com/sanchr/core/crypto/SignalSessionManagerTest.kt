package com.sanchr.core.crypto

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.sealed.SealedSenderCipher
import com.sanchr.core.crypto.sealed.SenderCertificateManager
import com.sanchr.core.crypto.store.SanchrIdentityKeyStore
import com.sanchr.core.crypto.store.SanchrKyberPreKeyStore
import com.sanchr.core.crypto.store.SanchrPreKeyStore
import com.sanchr.core.crypto.store.SanchrSenderKeyStore
import com.sanchr.core.crypto.store.SanchrSessionStore
import com.sanchr.core.crypto.store.SanchrSignalProtocolStore
import com.sanchr.core.crypto.store.SanchrSignedPreKeyStore
import com.sanchr.core.database.SanchrDatabase
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.keys.DeviceInfo
import com.sanchr.proto.keys.GetPreKeyBundleRequest
import com.sanchr.proto.keys.GetPreKeyCountRequest
import com.sanchr.proto.keys.GetUserDevicesRequest
import com.sanchr.proto.keys.GetUserDevicesResponse
import com.sanchr.proto.keys.KeyBundle
import com.sanchr.proto.keys.KeyServiceClient
import com.sanchr.proto.keys.KyberPreKey
import com.sanchr.proto.keys.OneTimePreKey
import com.sanchr.proto.keys.PreKeyBundleResponse
import com.sanchr.proto.keys.PreKeyCountResponse
import com.sanchr.proto.keys.SignedPreKey
import com.sanchr.proto.keys.UploadKeyBundleResponse
import com.sanchr.proto.keys.UploadOneTimePreKeysRequest
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

/**
 * Unit tests for [SignalSessionManager]. The highlight is an end-to-end
 * Alice → Bob round-trip (encrypt, decrypt, plaintext match) driven through
 * the real libsignal session machinery against two independent in-memory
 * Room stacks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SignalSessionManagerTest {
    // ----- Alice (sender) -----
    private lateinit var aliceDb: SanchrDatabase
    private lateinit var aliceStore: SanchrSignalProtocolStore
    private lateinit var aliceIdentityStore: SanchrIdentityKeyStore
    private lateinit var aliceKeyManager: SignalKeyManager
    private lateinit var aliceSessionManager: SignalSessionManager
    private lateinit var aliceFakeClient: FakeKeyServiceClient
    private lateinit var aliceCertManager: SenderCertificateManager
    private lateinit var aliceSealedCipher: SealedSenderCipher

    // ----- Bob (recipient) -----
    private lateinit var bobDb: SanchrDatabase
    private lateinit var bobStore: SanchrSignalProtocolStore
    private lateinit var bobIdentityStore: SanchrIdentityKeyStore
    private lateinit var bobPreKeyStore: SanchrPreKeyStore
    private lateinit var bobSignedPreKeyStore: SanchrSignedPreKeyStore
    private lateinit var bobKyberStore: SanchrKyberPreKeyStore

    private val aliceUserId = "11111111-1111-1111-1111-111111111111"
    private val aliceDeviceId = 1
    private val bobUserId = "22222222-2222-2222-2222-222222222222"
    private val bobDeviceId = 1
    private val bobRegistrationId = 8765

    private val dispatchers = ImmediateDispatcherProvider()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Alice stack.
        aliceDb =
            Room
                .inMemoryDatabaseBuilder(context, SanchrDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        aliceIdentityStore =
            SanchrIdentityKeyStore(
                aliceDb.accountDao(),
                aliceDb.signalIdentityDao(),
                InMemoryStagedIdentityStore(context),
            )
        val alicePreKeyStore = SanchrPreKeyStore(aliceDb.signalPreKeyDao())
        val aliceSignedPreKeyStore = SanchrSignedPreKeyStore(aliceDb.signalSignedPreKeyDao())
        val aliceSessionStore = SanchrSessionStore(aliceDb.signalSessionDao())
        val aliceSenderKeyStore = SanchrSenderKeyStore(context)
        val aliceKyberStore = SanchrKyberPreKeyStore(context)
        aliceStore =
            SanchrSignalProtocolStore(
                aliceIdentityStore,
                alicePreKeyStore,
                aliceSignedPreKeyStore,
                aliceSessionStore,
                aliceSenderKeyStore,
                aliceKyberStore,
            )

        val aliceIdentity = IdentityKeyPair.generate()
        aliceIdentityStore.storeIdentityKeyPair(aliceIdentity)
        aliceIdentityStore.storeLocalRegistrationId(4242)
        aliceIdentityStore.initializeAccount(aliceUserId, aliceDeviceId.toString(), "+10000000000")

        aliceFakeClient = FakeKeyServiceClient()
        aliceKeyManager =
            SignalKeyManager(
                store = aliceStore,
                identityKeyStore = aliceIdentityStore,
                preKeyStore = alicePreKeyStore,
                signedPreKeyStore = aliceSignedPreKeyStore,
                kyberPreKeyStore = aliceKyberStore,
                sessionManager = mockk<SessionManager>(relaxed = true),
                keyServiceClient = aliceFakeClient,
                dispatchers = dispatchers,
            )

        // Outbound is sealed-only (no non-sealed fallback), so provide a
        // deterministic non-empty envelope for every sealedEncrypt call.
        aliceSealedCipher = mockk<SealedSenderCipher>(relaxed = true)
        coEvery { aliceSealedCipher.sealedEncrypt(any(), any()) } answers {
            val plaintext = secondArg<ByteArray>()
            byteArrayOf(0x55) + plaintext + byteArrayOf(0x55)
        }
        // Default: cert manager succeeds. Individual tests reconfigure when
        // they need to exercise the failure path.
        aliceCertManager = mockk<SenderCertificateManager>(relaxed = true)

        aliceSessionManager =
            SignalSessionManager(
                store = aliceStore,
                keyManager = aliceKeyManager,
                dispatchers = dispatchers,
                sealedSenderCipher = aliceSealedCipher,
                senderCertificateManager = aliceCertManager,
                keyServiceClient = aliceFakeClient,
            )

        // Bob stack (built from scratch — we never call SignalSessionManager on Bob;
        // we drive his SessionCipher directly for the round-trip).
        bobDb =
            Room
                .inMemoryDatabaseBuilder(context, SanchrDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        bobIdentityStore =
            SanchrIdentityKeyStore(
                bobDb.accountDao(),
                bobDb.signalIdentityDao(),
                InMemoryStagedIdentityStore(context),
            )
        bobPreKeyStore = SanchrPreKeyStore(bobDb.signalPreKeyDao())
        bobSignedPreKeyStore = SanchrSignedPreKeyStore(bobDb.signalSignedPreKeyDao())
        val bobSessionStore = SanchrSessionStore(bobDb.signalSessionDao())
        val bobSenderKeyStore = SanchrSenderKeyStore(context)
        bobKyberStore = SanchrKyberPreKeyStore(context)
        bobStore =
            SanchrSignalProtocolStore(
                bobIdentityStore,
                bobPreKeyStore,
                bobSignedPreKeyStore,
                bobSessionStore,
                bobSenderKeyStore,
                bobKyberStore,
            )
    }

    @After
    fun tearDown() {
        aliceDb.close()
        bobDb.close()
    }

    // ------------------------------------------------------------------
    // hasSession / establishSession
    // ------------------------------------------------------------------

    @Test
    fun `hasSession returns false before any session and true after establishSession`() =
        runTest {
            assertFalse(aliceSessionManager.hasSession(bobUserId, bobDeviceId))

            aliceFakeClient.preKeyBundleResponse = publishBobsBundle()
            aliceSessionManager.establishSession(bobUserId, bobDeviceId)

            assertTrue(aliceSessionManager.hasSession(bobUserId, bobDeviceId))
        }

    @Test
    fun `establishSession fetches PreKeyBundle and runs X3DH to create a session`() =
        runTest {
            aliceFakeClient.preKeyBundleResponse = publishBobsBundle()

            aliceSessionManager.establishSession(bobUserId, bobDeviceId)

            assertEquals(1, aliceFakeClient.getPreKeyBundleCalls.size)
            val req = aliceFakeClient.getPreKeyBundleCalls.single()
            assertEquals(bobUserId, req.userId)
            assertEquals(bobDeviceId, req.deviceId)
            assertTrue(aliceStore.containsSession(SignalProtocolAddress(bobUserId, bobDeviceId)))
        }

    // ------------------------------------------------------------------
    // Encrypt
    // ------------------------------------------------------------------

    @Test
    fun `encrypt returns a non-empty ciphertext once a session exists`() =
        runTest {
            aliceFakeClient.preKeyBundleResponse = publishBobsBundle()

            val result = aliceSessionManager.encrypt("hello".toByteArray(), bobUserId, bobDeviceId)

            assertTrue(result.ciphertext.isNotEmpty())
            // First message in a fresh session is a PreKeySignalMessage (type 3).
            assertEquals(3, result.messageType)
        }

    // ------------------------------------------------------------------
    // Alice → Bob round-trip (the killer test)
    // ------------------------------------------------------------------

    @Test
    fun `decrypt round-trip — Alice encrypts and Bob decrypts with matching plaintext`() =
        runTest {
            aliceFakeClient.preKeyBundleResponse = publishBobsBundle()

            val plaintext = "sealed world at 2026-04-23".toByteArray()
            val result = aliceSessionManager.encrypt(plaintext, bobUserId, bobDeviceId)

            // Bob receives the ciphertext (a PreKeySignalMessage on the first hop)
            // and decrypts via his own SessionCipher against his own store.
            val aliceAddressOnBobsSide = SignalProtocolAddress(aliceUserId, aliceDeviceId)
            val bobCipher = SessionCipher(bobStore, aliceAddressOnBobsSide)
            val preKeyMessage = PreKeySignalMessage(result.ciphertext)
            // SignalSessionManager pads before encrypt; strip here to mirror
            // what the real receiving side (another SignalSessionManager) does.
            val decrypted = MessagePadding.strip(bobCipher.decrypt(preKeyMessage))

            assertTrue(plaintext.contentEquals(decrypted), "round-tripped plaintext must match")
            // And Bob now has a session with Alice.
            assertTrue(bobStore.containsSession(aliceAddressOnBobsSide))
        }

    // ------------------------------------------------------------------
    // Session reset
    // ------------------------------------------------------------------

    @Test
    fun `resetSession wipes only the target address and leaves peers untouched`() =
        runTest {
            val charlieUserId = "33333333-3333-3333-3333-333333333333"

            aliceFakeClient.preKeyBundleResponse = publishBobsBundle()
            aliceSessionManager.establishSession(bobUserId, bobDeviceId)

            aliceFakeClient.preKeyBundleResponse = publishPeerBundle(charlieUserId, bobDeviceId, 9999)
            aliceSessionManager.establishSession(charlieUserId, bobDeviceId)

            aliceSessionManager.resetSession(bobUserId, bobDeviceId)

            assertFalse(aliceStore.containsSession(SignalProtocolAddress(bobUserId, bobDeviceId)))
            assertTrue(aliceStore.containsSession(SignalProtocolAddress(charlieUserId, bobDeviceId)))
        }

    // ------------------------------------------------------------------
    // Multi-device fanout
    // ------------------------------------------------------------------

    @Test
    fun `encryptForAllDevices fans out across multiple device ids`() =
        runTest {
            // Bob has two key-capable devices.
            aliceFakeClient.userDevicesResponse =
                GetUserDevicesResponse(
                    devices =
                        listOf(
                            DeviceInfo(deviceId = 1, platform = "android", keyCapable = true),
                            DeviceInfo(deviceId = 2, platform = "ios", keyCapable = true),
                        ),
                )
            // Capture per-device bundle calls and answer each with a fresh Bob bundle.
            aliceFakeClient.preKeyBundleResponder = { req ->
                publishPeerBundle(req.userId, req.deviceId, bobRegistrationId + req.deviceId)
            }

            val results = aliceSessionManager.encryptForAllDevices("hi".toByteArray(), bobUserId)

            assertEquals(2, results.size)
            assertEquals(setOf(1, 2), results.map { it.deviceId }.toSet())
            results.forEach { assertTrue(it.ciphertext.isNotEmpty()) }
        }

    @Test
    fun `encryptForAllDevices propagates sender certificate failure instead of returning empty`() =
        runTest {
            // Recipient has one key-capable device so we get past the early
            // empty-device return.
            aliceFakeClient.userDevicesResponse =
                GetUserDevicesResponse(
                    devices = listOf(DeviceInfo(deviceId = 1, platform = "android", keyCapable = true)),
                )
            // Cert fetch is unavailable (e.g. network down on both cached
            // lookup and refresh). Prior to the fix this manifested as every
            // per-device sealedEncrypt throwing IllegalStateException, being
            // swallowed by the broad catch, and the method returning
            // emptyList() — which the send path then shipped to the server as
            // a valid SendMessage RPC with zero recipients.
            val certFailure = IllegalStateException("GetSenderCertificate returned empty")
            coEvery { aliceCertManager.current() } throws certFailure

            val thrown =
                assertFailsWith<IllegalStateException> {
                    aliceSessionManager.encryptForAllDevices("hi".toByteArray(), bobUserId)
                }
            assertEquals(certFailure, thrown)
        }

    // ------------------------------------------------------------------
    // Helpers — build Bob's PreKeyBundle response and materialize the keys
    // into Bob's own store so that a subsequent round-trip decrypts cleanly.
    // ------------------------------------------------------------------

    /**
     * Publishes Bob's prekey bundle: generates fresh SPK / OPK / KyberPK, stores
     * them in Bob's own DB, and returns the wire-level [PreKeyBundleResponse]
     * that Alice's fake client will hand back on `getPreKeyBundle`.
     */
    private fun publishBobsBundle(): PreKeyBundleResponse {
        val bobIdentity = requireBobIdentity()

        val signedPreKeyPair = ECKeyPair.generate()
        val signedPreKeyId = 42
        val signedPreKeySignature =
            bobIdentity.privateKey.calculateSignature(signedPreKeyPair.publicKey.serialize())
        val signedRecord =
            SignedPreKeyRecord(
                signedPreKeyId,
                System.currentTimeMillis(),
                signedPreKeyPair,
                signedPreKeySignature,
            )
        bobSignedPreKeyStore.storeSignedPreKey(signedPreKeyId, signedRecord)

        val oneTimePreKeyPair = ECKeyPair.generate()
        val oneTimePreKeyId = 7
        bobPreKeyStore.storePreKey(oneTimePreKeyId, PreKeyRecord(oneTimePreKeyId, oneTimePreKeyPair))

        val kyberKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberPreKeyId = 99
        val kyberSignature =
            bobIdentity.privateKey.calculateSignature(kyberKeyPair.publicKey.serialize())
        val kyberRecord =
            KyberPreKeyRecord(kyberPreKeyId, System.currentTimeMillis(), kyberKeyPair, kyberSignature)
        bobKyberStore.storeKyberPreKey(kyberPreKeyId, kyberRecord)

        return PreKeyBundleResponse(
            identityPublicKey = bobIdentity.publicKey.serialize(),
            signedPreKey =
                SignedPreKey(
                    keyId = signedPreKeyId,
                    publicKey = signedPreKeyPair.publicKey.serialize(),
                    signature = signedPreKeySignature,
                    timestamp = signedRecord.timestamp,
                ),
            oneTimePreKey =
                OneTimePreKey(
                    keyId = oneTimePreKeyId,
                    publicKey = oneTimePreKeyPair.publicKey.serialize(),
                ),
            registrationId = bobRegistrationId,
            deviceId = bobDeviceId,
            kyberPreKey =
                KyberPreKey(
                    keyId = kyberPreKeyId,
                    publicKey = kyberKeyPair.publicKey.serialize(),
                    signature = kyberSignature,
                    timestamp = kyberRecord.timestamp,
                ),
        )
    }

    /**
     * Synthesizes a standalone peer bundle (no backing Room row on the
     * receiving side — callers that don't decrypt can use this).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun publishPeerBundle(
        userId: String,
        deviceId: Int,
        registrationId: Int,
    ): PreKeyBundleResponse {
        val identity = IdentityKeyPair.generate()
        val spkPair = ECKeyPair.generate()
        val spkSignature = identity.privateKey.calculateSignature(spkPair.publicKey.serialize())
        val opk = ECKeyPair.generate()
        val kyber = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberSig = identity.privateKey.calculateSignature(kyber.publicKey.serialize())
        return PreKeyBundleResponse(
            identityPublicKey = identity.publicKey.serialize(),
            signedPreKey =
                SignedPreKey(
                    keyId = 1,
                    publicKey = spkPair.publicKey.serialize(),
                    signature = spkSignature,
                    timestamp = System.currentTimeMillis(),
                ),
            oneTimePreKey =
                OneTimePreKey(
                    keyId = 1,
                    publicKey = opk.publicKey.serialize(),
                ),
            registrationId = registrationId,
            deviceId = deviceId,
            kyberPreKey =
                KyberPreKey(
                    keyId = 1,
                    publicKey = kyber.publicKey.serialize(),
                    signature = kyberSig,
                    timestamp = System.currentTimeMillis(),
                ),
        )
    }

    /** Returns (or lazily initializes) Bob's long-lived identity. */
    private fun requireBobIdentity(): IdentityKeyPair {
        if (!bobIdentityStore.hasIdentityKeyPair()) {
            val bobIdentity = IdentityKeyPair.generate()
            bobIdentityStore.storeIdentityKeyPair(bobIdentity)
            bobIdentityStore.storeLocalRegistrationId(bobRegistrationId)
            bobIdentityStore.initializeAccount(bobUserId, bobDeviceId.toString(), "+20000000000")
        }
        return bobIdentityStore.getIdentityKeyPair()
    }

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    class FakeKeyServiceClient : KeyServiceClient {
        var preKeyCount: Int = 0
        var preKeyBundleResponse: PreKeyBundleResponse = PreKeyBundleResponse()
        var preKeyBundleResponder: ((GetPreKeyBundleRequest) -> PreKeyBundleResponse)? = null
        var userDevicesResponse: GetUserDevicesResponse = GetUserDevicesResponse()

        val uploadKeyBundleCalls = mutableListOf<KeyBundle>()
        val uploadOneTimePreKeysCalls = mutableListOf<UploadOneTimePreKeysRequest>()
        val getPreKeyBundleCalls = mutableListOf<GetPreKeyBundleRequest>()
        val getPreKeyCountCalls = mutableListOf<GetPreKeyCountRequest>()
        val getUserDevicesCalls = mutableListOf<GetUserDevicesRequest>()

        override suspend fun uploadKeyBundle(request: KeyBundle): UploadKeyBundleResponse {
            uploadKeyBundleCalls.add(request)
            return UploadKeyBundleResponse()
        }

        override suspend fun getPreKeyBundle(request: GetPreKeyBundleRequest): PreKeyBundleResponse {
            getPreKeyBundleCalls.add(request)
            return preKeyBundleResponder?.invoke(request) ?: preKeyBundleResponse
        }

        override suspend fun uploadOneTimePreKeys(request: UploadOneTimePreKeysRequest): PreKeyCountResponse {
            uploadOneTimePreKeysCalls.add(request)
            return PreKeyCountResponse(count = request.keys.size)
        }

        override suspend fun getPreKeyCount(request: GetPreKeyCountRequest): PreKeyCountResponse {
            getPreKeyCountCalls.add(request)
            return PreKeyCountResponse(count = preKeyCount)
        }

        override suspend fun getUserDevices(request: GetUserDevicesRequest): GetUserDevicesResponse {
            getUserDevicesCalls.add(request)
            return userDevicesResponse
        }
    }

    private class ImmediateDispatcherProvider : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
        override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    }

    // ------------------------------------------------------------------
    // Call payloads: iOS framing, no padding
    // ------------------------------------------------------------------

    @Test
    fun `encryptForCall yields a 0x01-framed PreKeySignalMessage that Bob decrypts unpadded`() =
        runTest {
            aliceFakeClient.preKeyBundleResponse = publishBobsBundle()
            val plaintext = """{"sdp":"v=0","dtls_fingerprint":"sha-256 AA","timestamp":1.0}""".toByteArray()

            val framed = aliceSessionManager.encryptForCall(plaintext, bobUserId, bobDeviceId)

            assertEquals(CallCiphertextFraming.PREKEY, framed[0])
            // Bob's side: dispatch on the type byte, decrypt on his own store; no padding to strip.
            val bobCipher = SessionCipher(bobStore, SignalProtocolAddress(aliceUserId, aliceDeviceId))
            val decrypted = bobCipher.decrypt(PreKeySignalMessage(framed.copyOfRange(1, framed.size)))
            assertTrue(plaintext.contentEquals(decrypted))
        }

    @Test
    fun `once the callee has replied, the next call payload is 0x02-framed and decryptForCall reads the reply`() =
        runTest {
            aliceFakeClient.preKeyBundleResponse = publishBobsBundle()
            val offer = aliceSessionManager.encryptForCall("offer".toByteArray(), bobUserId, bobDeviceId)
            val bobCipher = SessionCipher(bobStore, SignalProtocolAddress(aliceUserId, aliceDeviceId))
            bobCipher.decrypt(PreKeySignalMessage(offer.copyOfRange(1, offer.size)))

            // Bob answers on the session the offer created; Alice reads it through decryptForCall.
            val answer = CallCiphertextFraming.frame(bobCipher.encrypt("answer".toByteArray()))
            assertTrue("answer".toByteArray().contentEquals(aliceSessionManager.decryptForCall(answer, bobUserId, bobDeviceId)))

            // The session is acknowledged on Alice's side now: no more pre-key messages.
            val next = aliceSessionManager.encryptForCall("ice-restart".toByteArray(), bobUserId, bobDeviceId)
            assertEquals(CallCiphertextFraming.WHISPER, next[0])
        }

    @Test
    fun `decryptForCall reads the type byte and rejects an unknown one`() =
        runTest {
            aliceFakeClient.preKeyBundleResponse = publishBobsBundle()
            val framed = aliceSessionManager.encryptForCall("hello".toByteArray(), bobUserId, bobDeviceId)
            val bobCipher = SessionCipher(bobStore, SignalProtocolAddress(aliceUserId, aliceDeviceId))
            // Establish Bob's side of the session from the framed bytes the same way decryptForCall does.
            val (type, body) = CallCiphertextFraming.unframe(framed)
            assertEquals(CallCiphertextFraming.PREKEY, type)
            assertTrue("hello".toByteArray().contentEquals(bobCipher.decrypt(PreKeySignalMessage(body))))

            val bogus = byteArrayOf(0x07, 1, 2, 3)
            assertFailsWith<CallCiphertextFraming.UnknownTypeException> {
                aliceSessionManager.decryptForCall(bogus, bobUserId, bobDeviceId)
            }
        }

    @Test
    fun `encryptCallOffers resets and re-establishes every key-capable device, so each offer is a PreKey message`() =
        runTest {
            aliceFakeClient.userDevicesResponse =
                GetUserDevicesResponse(
                    devices =
                        listOf(
                            DeviceInfo(deviceId = 1, platform = "android", keyCapable = true),
                            DeviceInfo(deviceId = 2, platform = "ios", keyCapable = true),
                            DeviceInfo(deviceId = 3, platform = "web", keyCapable = false),
                        ),
                )
            // Device 1 is Bob's real identity (stable across bundle fetches);
            // device 2 is a separate identity, as a second device would be.
            aliceFakeClient.preKeyBundleResponder = { req ->
                if (req.deviceId ==
                    bobDeviceId
                ) {
                    publishBobsBundle()
                } else {
                    publishPeerBundle(req.userId, req.deviceId, bobRegistrationId + req.deviceId)
                }
            }
            // A pre-existing session with device 1 must not survive: a stale
            // ratchet is exactly what the reset protects the call from.
            aliceSessionManager.establishSession(bobUserId, bobDeviceId)
            aliceSessionManager.encrypt("warm".toByteArray(), bobUserId, bobDeviceId)

            val offers = aliceSessionManager.encryptCallOffers("offer".toByteArray(), bobUserId)

            assertEquals(setOf(1, 2), offers.map { it.deviceId }.toSet())
            offers.forEach { assertEquals(CallCiphertextFraming.PREKEY, it.ciphertext[0]) }
        }
}
