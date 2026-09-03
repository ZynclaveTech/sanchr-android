package com.sanchr.core.crypto

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.store.SanchrIdentityKeyStore
import com.sanchr.core.crypto.store.SanchrKyberPreKeyStore
import com.sanchr.core.crypto.store.SanchrPreKeyStore
import com.sanchr.core.crypto.store.SanchrSenderKeyStore
import com.sanchr.core.crypto.store.SanchrSessionStore
import com.sanchr.core.crypto.store.SanchrSignalProtocolStore
import com.sanchr.core.crypto.store.SanchrSignedPreKeyStore
import com.sanchr.core.database.SanchrDatabase
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.keys.GetPreKeyCountRequest
import com.sanchr.proto.keys.GetUserDevicesRequest
import com.sanchr.proto.keys.GetUserDevicesResponse
import com.sanchr.proto.keys.KeyBundle
import com.sanchr.proto.keys.KeyServiceClient
import com.sanchr.proto.keys.PreKeyBundleResponse
import com.sanchr.proto.keys.PreKeyCountResponse
import com.sanchr.proto.keys.UploadKeyBundleResponse
import com.sanchr.proto.keys.UploadOneTimePreKeysRequest
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

/**
 * Unit tests for [SignalKeyManager]. Exercises real libsignal key generation
 * paths against an in-memory Room DB, with a hand-rolled [FakeKeyServiceClient]
 * standing in for the backend so we can capture uploads and drive the
 * replenish/rotation branches deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SignalKeyManagerTest {
    private lateinit var db: SanchrDatabase
    private lateinit var store: SanchrSignalProtocolStore
    private lateinit var identityStore: SanchrIdentityKeyStore
    private lateinit var preKeyStore: SanchrPreKeyStore
    private lateinit var signedPreKeyStore: SanchrSignedPreKeyStore
    private lateinit var kyberStore: SanchrKyberPreKeyStore
    private lateinit var fakeClient: FakeKeyServiceClient
    private lateinit var sessionManager: SessionManager
    private lateinit var keyManager: SignalKeyManager

    private val dispatchers = ImmediateDispatcherProvider()

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SanchrDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        identityStore =
            SanchrIdentityKeyStore(
                db.accountDao(),
                db.signalIdentityDao(),
                InMemoryStagedIdentityStore(context),
            )
        preKeyStore = SanchrPreKeyStore(db.signalPreKeyDao())
        signedPreKeyStore = SanchrSignedPreKeyStore(db.signalSignedPreKeyDao())
        val sessionStore = SanchrSessionStore(db.signalSessionDao())
        val senderKeyStore = SanchrSenderKeyStore(context)
        kyberStore = SanchrKyberPreKeyStore(context)

        store =
            SanchrSignalProtocolStore(
                identityStore,
                preKeyStore,
                signedPreKeyStore,
                sessionStore,
                senderKeyStore,
                kyberStore,
            )

        fakeClient = FakeKeyServiceClient()
        // SessionManager is final + Context-bound — mockk handles both.
        sessionManager = mockk(relaxed = true)

        keyManager =
            SignalKeyManager(
                store = store,
                identityKeyStore = identityStore,
                preKeyStore = preKeyStore,
                signedPreKeyStore = signedPreKeyStore,
                kyberPreKeyStore = kyberStore,
                sessionManager = sessionManager,
                keyServiceClient = fakeClient,
                dispatchers = dispatchers,
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------

    @Test
    fun `generateIdentity persists identity keypair and registrationId to accounts`() =
        runTest {
            val pair = keyManager.generateIdentity()

            // generateIdentity stages in SanchrIdentityKeyStore until initializeAccount
            // is called (Task 2.1 two-step flow). Finish the flow and assert the DB row.
            identityStore.initializeAccount(userId = "u-1", deviceId = "1", phoneE164 = "+10000000000")

            val account = db.accountDao().getCurrentBlocking()
            assertNotNull(account)
            assertNotNull(account.identityPrivateKey)
            assertTrue(account.registrationId in 1..16380)
            // Staged pair survived into persistence.
            assertTrue(pair.serialize().contentEquals(identityStore.getIdentityKeyPair().serialize()))
        }

    // ------------------------------------------------------------------
    // One-time pre-keys
    // ------------------------------------------------------------------

    @Test
    fun `generateOneTimePreKeys produces requested batch and persists all`() =
        runTest {
            val records = keyManager.generateOneTimePreKeys(startId = 1, count = 100)

            assertEquals(100, records.size)
            assertEquals((1..100).toList(), records.map { it.id })
            assertEquals(100, db.signalPreKeyDao().count())
            // Spot-check persistence via the store.
            assertNotNull(preKeyStore.loadPreKey(1))
            assertNotNull(preKeyStore.loadPreKey(100))
        }

    // ------------------------------------------------------------------
    // Signed pre-keys
    // ------------------------------------------------------------------

    @Test
    fun `generateSignedPreKey persists and is readable by SignedPreKeyStore`() =
        runTest {
            keyManager.generateIdentity()
            identityStore.initializeAccount("u-1", "1", "+10000000000")
            val identityKeyPair = identityStore.getIdentityKeyPair()

            val record = keyManager.generateSignedPreKey(identityKeyPair)

            val reloaded = signedPreKeyStore.loadSignedPreKey(record.id)
            assertTrue(
                record.keyPair.publicKey.serialize().contentEquals(
                    reloaded.keyPair.publicKey.serialize(),
                ),
            )
            assertTrue(record.signature.contentEquals(reloaded.signature))
        }

    // ------------------------------------------------------------------
    // Kyber pre-keys
    // ------------------------------------------------------------------

    @Test
    fun `generateKyberPreKey does not throw and produces a persistable record`() =
        runTest {
            keyManager.generateIdentity()
            identityStore.initializeAccount("u-1", "1", "+10000000000")
            val identityKeyPair = identityStore.getIdentityKeyPair()

            val record = keyManager.generateKyberPreKey(identityKeyPair)

            assertNotNull(record)
            assertTrue(record.signature.isNotEmpty())
            assertTrue(
                record.keyPair.publicKey
                    .serialize()
                    .isNotEmpty(),
            )
        }

    // ------------------------------------------------------------------
    // Replenishment
    // ------------------------------------------------------------------

    @Test
    fun `checkAndReplenishPreKeys uploads a batch when count is below threshold`() =
        runTest {
            fakeClient.preKeyCount = 15 // below PRE_KEY_REPLENISH_THRESHOLD (20)

            keyManager.checkAndReplenishPreKeys()

            assertEquals(1, fakeClient.uploadOneTimePreKeysCalls.size)
            val uploaded = fakeClient.uploadOneTimePreKeysCalls.single()
            assertEquals(SignalKeyManager.PRE_KEY_BATCH_SIZE, uploaded.keys.size)
            // Ensure IDs are distinct and persisted to the local store too.
            assertEquals(
                SignalKeyManager.PRE_KEY_BATCH_SIZE,
                uploaded.keys
                    .map { it.keyId }
                    .toSet()
                    .size,
            )
            assertEquals(SignalKeyManager.PRE_KEY_BATCH_SIZE, db.signalPreKeyDao().count())
        }

    @Test
    fun `checkAndReplenishPreKeys does nothing when count is at or above threshold`() =
        runTest {
            fakeClient.preKeyCount = 50

            keyManager.checkAndReplenishPreKeys()

            assertTrue(fakeClient.uploadOneTimePreKeysCalls.isEmpty())
            assertEquals(0, db.signalPreKeyDao().count())
        }

    // ------------------------------------------------------------------
    // Rotation
    // ------------------------------------------------------------------

    @Test
    fun `rotateSignedPreKeyIfNeeded generates and uploads a new signed pre-key when existing is older than 7 days`() =
        runTest {
            // Seed identity.
            keyManager.generateIdentity()
            identityStore.initializeAccount("u-1", "1", "+10000000000")
            val identity = identityStore.getIdentityKeyPair()

            // Seed a signed pre-key 8 days old (older than the 7-day rotation window).
            val oldTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8)
            val kp = ECKeyPair.generate()
            val signature = identity.privateKey.calculateSignature(kp.publicKey.serialize())
            val oldRecord = SignedPreKeyRecord(1, oldTimestamp, kp, signature)
            signedPreKeyStore.storeSignedPreKey(1, oldRecord)

            keyManager.rotateSignedPreKeyIfNeeded()

            // A new signed pre-key should have been uploaded.
            assertEquals(1, fakeClient.uploadKeyBundleCalls.size)
            val bundle = fakeClient.uploadKeyBundleCalls.single()
            assertNotNull(bundle.signedPreKey)
            assertNotNull(bundle.kyberPreKey)

            // And both signed pre-keys (old + new) exist pre-cleanup, or at minimum
            // the newly-generated one persisted with id > 1.
            val all = signedPreKeyStore.loadSignedPreKeys()
            assertTrue(all.any { it.id > 1 }, "expected a newly-generated signed pre-key")
        }

    @Test
    fun `rotateSignedPreKeyIfNeeded is a no-op when latest signed pre-key is fresh`() =
        runTest {
            keyManager.generateIdentity()
            identityStore.initializeAccount("u-1", "1", "+10000000000")
            val identity = identityStore.getIdentityKeyPair()

            // Fresh signed pre-key — one hour old.
            val freshTimestamp = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
            val kp = ECKeyPair.generate()
            val signature = identity.privateKey.calculateSignature(kp.publicKey.serialize())
            signedPreKeyStore.storeSignedPreKey(1, SignedPreKeyRecord(1, freshTimestamp, kp, signature))

            keyManager.rotateSignedPreKeyIfNeeded()

            assertTrue(fakeClient.uploadKeyBundleCalls.isEmpty())
            assertEquals(1, signedPreKeyStore.loadSignedPreKeys().size)
        }

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    /**
     * Hand-rolled test double for [KeyServiceClient]. Captures each RPC into
     * a per-method list and returns canned responses driven by public mutable
     * fields — simpler than mockk for the cases we need.
     */
    class FakeKeyServiceClient : KeyServiceClient {
        var preKeyCount: Int = 0
        var preKeyBundleResponse: PreKeyBundleResponse = PreKeyBundleResponse()
        var userDevicesResponse: GetUserDevicesResponse = GetUserDevicesResponse()

        val uploadKeyBundleCalls = mutableListOf<KeyBundle>()
        val uploadOneTimePreKeysCalls = mutableListOf<UploadOneTimePreKeysRequest>()
        val getPreKeyBundleCalls = mutableListOf<com.sanchr.proto.keys.GetPreKeyBundleRequest>()
        val getPreKeyCountCalls = mutableListOf<GetPreKeyCountRequest>()
        val getUserDevicesCalls = mutableListOf<GetUserDevicesRequest>()

        override suspend fun uploadKeyBundle(request: KeyBundle): UploadKeyBundleResponse {
            uploadKeyBundleCalls.add(request)
            return UploadKeyBundleResponse()
        }

        override suspend fun getPreKeyBundle(request: com.sanchr.proto.keys.GetPreKeyBundleRequest): PreKeyBundleResponse {
            getPreKeyBundleCalls.add(request)
            return preKeyBundleResponse
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

    /**
     * Minimal in-test [DispatcherProvider] — runs every dispatcher on
     * [Dispatchers.Unconfined] so `withContext(...)` inside suspend calls
     * executes synchronously under `runTest`.
     */
    private class ImmediateDispatcherProvider : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
        override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    }
}
