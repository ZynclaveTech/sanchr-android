package com.sanchr.core.crypto

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.sanchr.core.database.crypto.DatabasePassphraseProvider
import com.sanchr.core.datastore.SessionManager
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
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

/**
 * End-to-end round-trip integration test that wires up the full E2EE stack
 * (identity / pre-key / signed-pre-key / session stores + key manager +
 * session manager) against **real SQLCipher-encrypted Room databases**.
 *
 * Unit tests ([SignalSessionManagerTest] in `src/test`) already cover the
 * Alice → Bob round-trip against in-memory Room. This instrumentation test
 * complements that by proving the same flow survives:
 *
 *  1. SQLCipher passphrase derivation via [DatabasePassphraseProvider]
 *     (EncryptedSharedPreferences + AndroidKeyStore-wrapped 256-bit key).
 *  2. Physical encrypted DB file I/O on the device.
 *  3. Close + reopen of the DB with the same passphrase.
 *
 * Backend is faked with a shared [FakeKeyServiceClient] that bridges Alice
 * and Bob (Alice uploads her bundle → Bob fetches it, and vice versa).
 */
@RunWith(AndroidJUnit4::class)
class RoundTripIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var passphraseProvider: DatabasePassphraseProvider
    private lateinit var fakeBackend: FakeKeyServiceClient

    private lateinit var alice: Stack
    private lateinit var bob: Stack

    private val aliceUserId: String = "11111111-1111-1111-1111-111111111111"
    private val bobUserId: String = "22222222-2222-2222-2222-222222222222"
    private val deviceId: Int = 1

    private val aliceDbFile: String = "alice-e2ee-test.db"
    private val bobDbFile: String = "bob-e2ee-test.db"

    private val dispatchers = ImmediateDispatcherProvider()

    // ------------------------------------------------------------------
    // Setup / teardown
    // ------------------------------------------------------------------

    @Before
    fun setUp() {
        // Start clean: wipe any stale SQLCipher DB files from previous runs
        // (the EncryptedSharedPreferences-wrapped passphrase would also change
        // if we re-key, so the DB file MUST not survive between runs).
        context.deleteDatabase(aliceDbFile)
        context.deleteDatabase(bobDbFile)

        passphraseProvider = DatabasePassphraseProvider(context)
        fakeBackend = FakeKeyServiceClient()

        alice = buildStack(aliceDbFile, aliceUserId, phone = "+10000000000", registrationId = 4242)
        bob = buildStack(bobDbFile, bobUserId, phone = "+20000000000", registrationId = 8765)
    }

    @After
    fun tearDown() {
        runCatching { alice.db.close() }
        runCatching { bob.db.close() }
        context.deleteDatabase(aliceDbFile)
        context.deleteDatabase(bobDbFile)
        runCatching { passphraseProvider.wipe() }
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun alice_and_bob_establish_session_and_round_trip_message() =
        runBlocking {
            // 1. Bob publishes his pre-key bundle to the fake backend.
            publishBundle(bob)

            // 2. Alice fetches Bob's bundle and establishes a session.
            alice.sessionManager.establishSession(bobUserId, deviceId)
            assertTrue(alice.sessionManager.hasSession(bobUserId, deviceId))

            // 3. Alice encrypts "hello from alice" → PreKeySignalMessage.
            val plaintext1 = "hello from alice".toByteArray()
            val result1 = alice.sessionManager.encrypt(plaintext1, bobUserId, deviceId)
            assertEquals(3, result1.messageType)
            assertTrue(result1.ciphertext.isNotEmpty())

            // 4. Bob decrypts via his own SessionCipher — this is the first hop,
            //    so we hand him a PreKeySignalMessage and he builds his side
            //    of the session while decrypting.
            val aliceAddrOnBob = SignalProtocolAddress(aliceUserId, deviceId)
            val bobCipher = SessionCipher(bob.unifiedStore, aliceAddrOnBob)
            // SessionManager pads outbound; mirror by stripping on the raw-cipher side.
            val decrypted1 =
                MessagePadding.strip(bobCipher.decrypt(PreKeySignalMessage(result1.ciphertext)))
            assertTrue(plaintext1.contentEquals(decrypted1), "first round-trip plaintext must match")
            assertTrue(bob.unifiedStore.containsSession(aliceAddrOnBob))

            // 5. Bob replies with an established-session SignalMessage (type 1);
            //    Alice decrypts. Bob must also pad so Alice's strip() sees a sentinel.
            val plaintext2 = "hi alice".toByteArray()
            val ciphertext2 = bobCipher.encrypt(MessagePadding.pad(plaintext2))
            val decrypted2 =
                alice.sessionManager.decrypt(
                    ciphertext = ciphertext2.serialize(),
                    senderId = bobUserId,
                    senderDevice = deviceId,
                )
            assertTrue(plaintext2.contentEquals(decrypted2), "reply round-trip plaintext must match")
        }

    @Test
    fun survive_database_close_and_reopen() =
        runBlocking {
            publishBundle(bob)
            alice.sessionManager.establishSession(bobUserId, deviceId)

            val first = "before reopen".toByteArray()
            val firstResult = alice.sessionManager.encrypt(first, bobUserId, deviceId)

            // Bob consumes the PreKeySignalMessage so his side is live.
            val aliceAddrOnBob = SignalProtocolAddress(aliceUserId, deviceId)
            val bobCipher = SessionCipher(bob.unifiedStore, aliceAddrOnBob)
            val decryptedFirst =
                MessagePadding.strip(bobCipher.decrypt(PreKeySignalMessage(firstResult.ciphertext)))
            assertTrue(first.contentEquals(decryptedFirst))

            // Close Alice's DB and reopen against the same on-disk file and
            // the same passphrase — this proves SQLCipher persistence +
            // store serialization survive a cold restart.
            alice.db.close()
            alice = buildStack(aliceDbFile, aliceUserId, phone = "+10000000000", registrationId = 4242)

            // Session still present after reopen.
            assertTrue(alice.sessionManager.hasSession(bobUserId, deviceId))

            val second = "after reopen".toByteArray()
            val secondResult = alice.sessionManager.encrypt(second, bobUserId, deviceId)
            // Already-established session → SignalMessage (type 2), NOT PreKey.
            val decryptedSecond =
                MessagePadding.strip(bobCipher.decrypt(SignalMessage(secondResult.ciphertext)))
            assertTrue(second.contentEquals(decryptedSecond), "post-reopen round-trip plaintext must match")
        }

    // ------------------------------------------------------------------
    // Helpers — build a full crypto stack backed by an SQLCipher Room DB.
    // ------------------------------------------------------------------

    private fun buildStack(
        dbFilename: String,
        userId: String,
        phone: String,
        registrationId: Int,
    ): Stack {
        // The SQLCipher native lib is loaded implicitly by SupportOpenHelperFactory,
        // but we also touch it defensively to mirror production initialization.
        System.loadLibrary("sqlcipher")

        // Scoped access: the provider zeroes its buffer on block exit; we hand a
        // copyOf() to the factory because it retains the byte[] by reference.
        val db =
            Room
                .databaseBuilder(context, SanchrDatabase::class.java, dbFilename)
                .openHelperFactory(
                    passphraseProvider.withPassphrase { passphrase -> SupportOpenHelperFactory(passphrase.copyOf()) },
                ).allowMainThreadQueries()
                .fallbackToDestructiveMigration()
                .build()

        val identityStore =
            SanchrIdentityKeyStore(
                db.accountDao(),
                db.signalIdentityDao(),
                StagedIdentityStore(context),
            )
        val preKeyStore = SanchrPreKeyStore(db.signalPreKeyDao())
        val signedPreKeyStore = SanchrSignedPreKeyStore(db.signalSignedPreKeyDao())
        val sessionStore = SanchrSessionStore(db.signalSessionDao())
        val senderKeyStore = SanchrSenderKeyStore(context)
        val kyberStore = SanchrKyberPreKeyStore(context)
        val unifiedStore =
            SanchrSignalProtocolStore(
                identityStore,
                preKeyStore,
                signedPreKeyStore,
                sessionStore,
                senderKeyStore,
                kyberStore,
            )

        // Initialize identity only the first time the DB is opened.
        if (!identityStore.hasIdentityKeyPair()) {
            val identity = IdentityKeyPair.generate()
            identityStore.storeIdentityKeyPair(identity)
            identityStore.storeLocalRegistrationId(registrationId)
            identityStore.initializeAccount(userId, deviceId.toString(), phone)
        }

        val keyManager =
            SignalKeyManager(
                store = unifiedStore,
                identityKeyStore = identityStore,
                preKeyStore = preKeyStore,
                signedPreKeyStore = signedPreKeyStore,
                kyberPreKeyStore = kyberStore,
                sessionManager = mockk<SessionManager>(relaxed = true),
                keyServiceClient = fakeBackend,
                dispatchers = dispatchers,
            )

        // Sealed sender is out of scope for this test — force the fallback path.
        val sealed = mockk<SealedSenderCipher>(relaxed = true)
        coEvery { sealed.sealedEncrypt(any(), any()) } throws IllegalStateException("no cert in tests")

        val sessionManager =
            SignalSessionManager(
                store = unifiedStore,
                keyManager = keyManager,
                dispatchers = dispatchers,
                sealedSenderCipher = sealed,
                senderCertificateManager = mockk<SenderCertificateManager>(relaxed = true),
                keyServiceClient = fakeBackend,
            )

        return Stack(
            db = db,
            identityStore = identityStore,
            preKeyStore = preKeyStore,
            signedPreKeyStore = signedPreKeyStore,
            kyberStore = kyberStore,
            unifiedStore = unifiedStore,
            keyManager = keyManager,
            sessionManager = sessionManager,
            userId = userId,
        )
    }

    /**
     * Generates a fresh SPK / OPK / Kyber PK for [stack], persists them in
     * that stack's own stores (so the owner can later decrypt), and wires
     * the corresponding [PreKeyBundleResponse] into the fake backend under
     * the stack's userId + deviceId.
     */
    private fun publishBundle(stack: Stack) {
        val identity = stack.identityStore.getIdentityKeyPair()
        val registrationId = stack.identityStore.getLocalRegistrationId()

        val spkPair = ECKeyPair.generate()
        val spkId = 42
        val spkSig = identity.privateKey.calculateSignature(spkPair.publicKey.serialize())
        val spkRecord = SignedPreKeyRecord(spkId, System.currentTimeMillis(), spkPair, spkSig)
        stack.signedPreKeyStore.storeSignedPreKey(spkId, spkRecord)

        val opkPair = ECKeyPair.generate()
        val opkId = 7
        stack.preKeyStore.storePreKey(opkId, PreKeyRecord(opkId, opkPair))

        val kyberPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberId = 99
        val kyberSig = identity.privateKey.calculateSignature(kyberPair.publicKey.serialize())
        val kyberRecord = KyberPreKeyRecord(kyberId, System.currentTimeMillis(), kyberPair, kyberSig)
        stack.kyberStore.storeKyberPreKey(kyberId, kyberRecord)

        val response =
            PreKeyBundleResponse(
                identityPublicKey = identity.publicKey.serialize(),
                signedPreKey =
                    SignedPreKey(
                        keyId = spkId,
                        publicKey = spkPair.publicKey.serialize(),
                        signature = spkSig,
                        timestamp = spkRecord.timestamp,
                    ),
                oneTimePreKey =
                    OneTimePreKey(
                        keyId = opkId,
                        publicKey = opkPair.publicKey.serialize(),
                    ),
                registrationId = registrationId,
                deviceId = deviceId,
                kyberPreKey =
                    KyberPreKey(
                        keyId = kyberId,
                        publicKey = kyberPair.publicKey.serialize(),
                        signature = kyberSig,
                        timestamp = kyberRecord.timestamp,
                    ),
            )
        fakeBackend.publish(stack.userId, deviceId, response)
    }

    // ------------------------------------------------------------------
    // Types
    // ------------------------------------------------------------------

    /**
     * A fully wired E2EE stack for a single user — each [Stack] owns its
     * own SQLCipher-encrypted [SanchrDatabase] file.
     */
    private class Stack(
        val db: SanchrDatabase,
        val identityStore: SanchrIdentityKeyStore,
        val preKeyStore: SanchrPreKeyStore,
        val signedPreKeyStore: SanchrSignedPreKeyStore,
        val kyberStore: SanchrKyberPreKeyStore,
        val unifiedStore: SanchrSignalProtocolStore,
        val keyManager: SignalKeyManager,
        val sessionManager: SignalSessionManager,
        val userId: String,
    )

    /**
     * In-memory fake of [KeyServiceClient] that stores pre-key bundles in a
     * map keyed by (userId, deviceId). Both Alice's and Bob's stacks share
     * this instance — when Bob publishes his bundle, Alice can fetch it.
     *
     * Intentionally duplicated (not shared with `src/test`) because Gradle
     * does not bridge test source sets without a testFixtures configuration.
     */
    private class FakeKeyServiceClient : KeyServiceClient {
        private val bundles: MutableMap<Pair<String, Int>, PreKeyBundleResponse> =
            mutableMapOf()

        fun publish(
            userId: String,
            deviceId: Int,
            response: PreKeyBundleResponse,
        ) {
            bundles[userId to deviceId] = response
        }

        override suspend fun uploadKeyBundle(request: KeyBundle): UploadKeyBundleResponse = UploadKeyBundleResponse()

        override suspend fun getPreKeyBundle(request: GetPreKeyBundleRequest): PreKeyBundleResponse =
            bundles[request.userId to request.deviceId]
                ?: error("no bundle published for ${request.userId}:${request.deviceId}")

        override suspend fun uploadOneTimePreKeys(request: UploadOneTimePreKeysRequest): PreKeyCountResponse =
            PreKeyCountResponse(count = request.keys.size)

        override suspend fun getPreKeyCount(request: GetPreKeyCountRequest): PreKeyCountResponse = PreKeyCountResponse(count = 0)

        override suspend fun getUserDevices(request: GetUserDevicesRequest): GetUserDevicesResponse = GetUserDevicesResponse()
    }

    private class ImmediateDispatcherProvider : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
        override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    }
}
