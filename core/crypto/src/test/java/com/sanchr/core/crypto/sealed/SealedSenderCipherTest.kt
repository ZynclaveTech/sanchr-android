package com.sanchr.core.crypto.sealed

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
import com.sanchr.core.database.entity.AccountEntity
import java.util.Optional
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
import org.signal.libsignal.metadata.certificate.SenderCertificate
import org.signal.libsignal.metadata.certificate.ServerCertificate
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.PreKeyBundle

/**
 * Unit tests for the sealed-sender layer.
 *
 * This test exercises the **encrypt path** end-to-end (identity + X3DH +
 * sealed-sender encrypt) plus the supporting machinery (TrustRoot validation,
 * SenderCertificateManager caching). A full Alice→Bob round-trip across two
 * independent DB stacks is deferred to Task 7's androidTest — setting that up
 * in Robolectric requires two full Room instances and is called out in the
 * M2 plan as acceptable to defer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SealedSenderCipherTest {
    private lateinit var db: SanchrDatabase
    private lateinit var store: SanchrSignalProtocolStore
    private lateinit var identityStore: SanchrIdentityKeyStore

    // Test-only trust root keypair; stands in for the production TrustRoot
    // until M3 wires a real one via BuildConfig.
    private lateinit var trustRootKeyPair: ECKeyPair
    private lateinit var serverKeyPair: ECKeyPair
    private lateinit var serverCertificate: ServerCertificate

    // Alice (sender) identity + account
    private val aliceUuid: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val aliceDeviceId: Int = 1
    private lateinit var aliceIdentity: IdentityKeyPair

    // Bob (recipient) identity, used to build a PreKeyBundle for X3DH.
    private val bobUuid: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val bobDeviceId: Int = 1
    private val bobRegistrationId: Int = 8765
    private lateinit var bobIdentity: IdentityKeyPair

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

        identityStore =
            SanchrIdentityKeyStore(
                db.accountDao(),
                db.signalIdentityDao(),
                com.sanchr.core.crypto.InMemoryStagedIdentityStore(
                    ApplicationProvider.getApplicationContext(),
                ),
            )
        val preKeyStore = SanchrPreKeyStore(db.signalPreKeyDao())
        val signedPreKeyStore = SanchrSignedPreKeyStore(db.signalSignedPreKeyDao())
        val sessionStore = SanchrSessionStore(db.signalSessionDao())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val senderKeyStore = SanchrSenderKeyStore(context)
        val kyberStore = SanchrKyberPreKeyStore(context)

        store =
            SanchrSignalProtocolStore(
                identityStore,
                preKeyStore,
                signedPreKeyStore,
                sessionStore,
                senderKeyStore,
                kyberStore,
            )

        // Alice account + identity.
        aliceIdentity = IdentityKeyPair.generate()
        identityStore.storeIdentityKeyPair(aliceIdentity)
        identityStore.storeLocalRegistrationId(4242)
        identityStore.initializeAccount(
            userId = aliceUuid.toString(),
            deviceId = aliceDeviceId.toString(),
            phoneE164 = "+10000000000",
        )

        // Bob: just an identity used to build a PreKeyBundle below.
        bobIdentity = IdentityKeyPair.generate()

        // Test trust-root + server cert used to mint Alice's SenderCertificate.
        trustRootKeyPair = ECKeyPair.generate()
        serverKeyPair = ECKeyPair.generate()
        serverCertificate =
            ServerCertificate(
                trustRootKeyPair.privateKey,
                // keyId =
                1,
                serverKeyPair.publicKey,
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------------------
    // TrustRootProvider
    // ------------------------------------------------------------------

    @Test
    fun `BuildConfigTrustRootProvider throws when SEALED_SENDER_TRUST_ROOT is empty`() {
        val provider = BuildConfigTrustRootProvider()
        // M2 default: empty — this is the explicit, expected behaviour until M3.
        assertFailsWith<IllegalStateException> { provider.trustRoot() }
    }

    // ------------------------------------------------------------------
    // SenderCertificateManager
    // ------------------------------------------------------------------

    @Test
    fun `SenderCertificateManager refresh throws — M2 stub`() =
        runTest {
            val mgr = SenderCertificateManager(dispatchers)
            assertFailsWith<UnsupportedOperationException> { mgr.refresh() }
        }

    @Test
    fun `SenderCertificateManager returns cached cert while not near expiry`() =
        runTest {
            val mgr = SenderCertificateManager(dispatchers)
            val cert = issueAliceCertificate(expiresInMs = TimeUnit.DAYS.toMillis(7))
            mgr.setForTest(cert)

            val got = mgr.current()
            assertEquals(cert.senderUuid, got.senderUuid)
            assertEquals(cert.senderDeviceId, got.senderDeviceId)
        }

    @Test
    fun `SenderCertificateManager refreshes when cached cert is within 24h of expiry`() =
        runTest {
            val mgr = SenderCertificateManager(dispatchers)
            // Expires in 1h — well inside the 24h grace window, so `current()` must
            // attempt refresh. The stubbed refresh RPC throws; that's the signal.
            val expiringCert = issueAliceCertificate(expiresInMs = TimeUnit.HOURS.toMillis(1))
            mgr.setForTest(expiringCert)

            assertFailsWith<UnsupportedOperationException> { mgr.current() }
        }

    // ------------------------------------------------------------------
    // SealedSenderCipher — encrypt path
    // ------------------------------------------------------------------

    @Test
    fun `sealedEncrypt produces non-empty envelope once session is established`() =
        runTest {
            // Build Bob's prekey bundle and process it into Alice's session store.
            val bobAddress = SignalProtocolAddress(bobUuid.toString(), bobDeviceId)
            val bobBundle = buildBobPreKeyBundle()
            SessionBuilder(store, bobAddress).process(bobBundle)

            val certManager = SenderCertificateManager(dispatchers)
            certManager.setForTest(issueAliceCertificate(expiresInMs = TimeUnit.DAYS.toMillis(7)))

            val cipher =
                SealedSenderCipher(
                    store = store,
                    certManager = certManager,
                    trustRootProvider = TrustRootProvider { trustRootKeyPair.publicKey },
                    dispatchers = dispatchers,
                )

            val envelope =
                cipher.sealedEncrypt(bobAddress, "hello, sealed world".toByteArray())

            assertNotNull(envelope)
            // Sealed-sender envelopes are non-trivially larger than the plaintext
            // (type byte + ephemeral pubkey + MAC + underlying session ciphertext).
            assertTrue(envelope.isNotEmpty(), "sealed envelope should be non-empty")
            assertTrue(envelope.size > 32, "sealed envelope should include auth material")
        }

    @Test
    fun `sealedDecrypt throws when TrustRoot is not configured`() =
        runTest {
            val certManager = SenderCertificateManager(dispatchers)
            val cipher =
                SealedSenderCipher(
                    store = store,
                    certManager = certManager,
                    trustRootProvider = BuildConfigTrustRootProvider(),
                    dispatchers = dispatchers,
                )
            assertFailsWith<IllegalStateException> {
                cipher.sealedDecrypt(ByteArray(32), System.currentTimeMillis())
            }
        }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun issueAliceCertificate(expiresInMs: Long): SenderCertificate {
        val expiration = System.currentTimeMillis() + expiresInMs
        return serverCertificate.issue(
            serverKeyPair.privateKey,
            aliceUuid.toString(),
            Optional.empty<String>(),
            aliceDeviceId,
            aliceIdentity.publicKey.publicKey,
            expiration,
        )
    }

    private fun buildBobPreKeyBundle(): PreKeyBundle {
        val signedPreKeyPair = ECKeyPair.generate()
        val signedPreKeyId = 42
        val signedPreKeySignature =
            bobIdentity.privateKey.calculateSignature(
                signedPreKeyPair.publicKey.serialize(),
            )

        val oneTimePreKeyPair = ECKeyPair.generate()
        val oneTimePreKeyId = 7

        val kyberKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberPreKeyId = 99
        val kyberSignature =
            bobIdentity.privateKey.calculateSignature(kyberKeyPair.publicKey.serialize())

        return PreKeyBundle(
            bobRegistrationId,
            bobDeviceId,
            oneTimePreKeyId,
            oneTimePreKeyPair.publicKey,
            signedPreKeyId,
            signedPreKeyPair.publicKey,
            signedPreKeySignature,
            bobIdentity.publicKey,
            kyberPreKeyId,
            kyberKeyPair.publicKey,
            kyberSignature,
        )
    }

    /**
     * Minimal in-test [DispatcherProvider] — runs every dispatcher on
     * [Dispatchers.Unconfined] so `withContext(...)` inside suspend calls
     * executes synchronously in `runTest`.
     */
    private class ImmediateDispatcherProvider : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
        override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    }

    @Suppress("unused")
    private fun unused(
        account: AccountEntity,
        key: ECPublicKey,
    ) = Unit
}
