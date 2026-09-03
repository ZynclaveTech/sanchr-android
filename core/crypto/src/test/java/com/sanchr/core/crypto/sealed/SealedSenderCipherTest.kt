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
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.messaging.AckMessagesRequest
import com.sanchr.proto.messaging.AckMessagesResponse
import com.sanchr.proto.messaging.ClientEvent
import com.sanchr.proto.messaging.Conversation
import com.sanchr.proto.messaging.DeleteMessageRequest
import com.sanchr.proto.messaging.DeleteMessageResponse
import com.sanchr.proto.messaging.EncryptedEnvelope
import com.sanchr.proto.messaging.GetConversationsRequest
import com.sanchr.proto.messaging.GetConversationsResponse
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.ReceiptRequest
import com.sanchr.proto.messaging.ReceiptResponse
import com.sanchr.proto.messaging.SendMessageRequest
import com.sanchr.proto.messaging.SendMessageResponse
import com.sanchr.proto.messaging.SenderCertificateRequest
import com.sanchr.proto.messaging.SenderCertificateResponse
import com.sanchr.proto.messaging.ServerEvent
import com.sanchr.proto.messaging.StartDirectConversationRequest
import com.sanchr.proto.messaging.SyncRequest
import io.mockk.mockk
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
    fun `BuildConfigTrustRootProvider returns a valid Curve25519 ECPublicKey for the production sanchr-prod default`() {
        // M3 wiring (commit on feat/android-auth-onboarding-realignment): the
        // sanchr-prod trust-root pubkey is baked in as the default value of
        // `sanchr.sealedSenderTrustRoot` in `core/crypto/build.gradle.kts`,
        // derived from the backend's `auth.sealed_sender_key` via
        // `cargo run -p sanchr-server-crypto --bin print-trust-root`.
        // Anything else (empty, malformed, wrong type byte) must fail-fast at
        // first decrypt so we never silently accept an unverified envelope.
        val key = BuildConfigTrustRootProvider().trustRoot()
        val bytes = key.serialize()
        assertEquals(33, bytes.size)
        assertEquals(0x05.toByte(), bytes[0])
    }

    // ------------------------------------------------------------------
    // SenderCertificateManager
    // ------------------------------------------------------------------

    @Test
    fun `SenderCertificateManager refresh fetches over MessagingService and caches`() =
        runTest {
            val cert = issueAliceCertificate(expiresInMs = TimeUnit.DAYS.toMillis(7))
            val fakeClient = FakeMessagingClient(certificateBytes = cert.serialized)
            val fakeSession = mockk<SessionManager>(relaxed = true)

            val mgr = SenderCertificateManager(dispatchers, fakeClient, fakeSession)
            val got = mgr.refresh()

            assertEquals(1, fakeClient.getSenderCertificateCalls)
            assertEquals(cert.senderUuid, got.senderUuid)
            // A second `current()` inside the grace window must serve from cache.
            val again = mgr.current()
            assertEquals(cert.senderUuid, again.senderUuid)
            assertEquals(1, fakeClient.getSenderCertificateCalls)
        }

    @Test
    fun `SenderCertificateManager refresh throws on empty certificate response`() =
        runTest {
            val fakeClient = FakeMessagingClient(certificateBytes = ByteArray(0))
            val fakeSession = mockk<SessionManager>(relaxed = true)
            val mgr = SenderCertificateManager(dispatchers, fakeClient, fakeSession)

            assertFailsWith<IllegalStateException> { mgr.refresh() }
        }

    @Test
    fun `SenderCertificateManager returns cached cert while not near expiry`() =
        runTest {
            val mgr =
                SenderCertificateManager(
                    dispatchers,
                    FakeMessagingClient(),
                    mockk<SessionManager>(relaxed = true),
                )
            val cert = issueAliceCertificate(expiresInMs = TimeUnit.DAYS.toMillis(7))
            mgr.setForTest(cert)

            val got = mgr.current()
            assertEquals(cert.senderUuid, got.senderUuid)
            assertEquals(cert.senderDeviceId, got.senderDeviceId)
        }

    @Test
    fun `SenderCertificateManager refreshes when cached cert is within 24h of expiry`() =
        runTest {
            val fresh = issueAliceCertificate(expiresInMs = TimeUnit.DAYS.toMillis(7))
            val fakeClient = FakeMessagingClient(certificateBytes = fresh.serialized)
            val mgr =
                SenderCertificateManager(
                    dispatchers,
                    fakeClient,
                    mockk<SessionManager>(relaxed = true),
                )
            val expiringCert = issueAliceCertificate(expiresInMs = TimeUnit.HOURS.toMillis(1))
            mgr.setForTest(expiringCert)

            val got = mgr.current()
            assertEquals(1, fakeClient.getSenderCertificateCalls)
            assertEquals(fresh.senderUuid, got.senderUuid)
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

            val certManager =
                SenderCertificateManager(
                    dispatchers,
                    FakeMessagingClient(),
                    mockk<SessionManager>(relaxed = true),
                )
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
    fun `sealedDecrypt rejects garbage envelope bytes loudly`() =
        runTest {
            // Pre-M3 this test asserted IllegalStateException because the
            // TrustRoot was unconfigured. With the prod default now baked in,
            // trustRoot() succeeds, and libsignal proceeds far enough to fail
            // protobuf-decoding the envelope. The test still defends the same
            // property — random bytes must not silently decrypt — just at the
            // next layer down.
            val certManager =
                SenderCertificateManager(
                    dispatchers,
                    FakeMessagingClient(),
                    mockk<SessionManager>(relaxed = true),
                )
            val cipher =
                SealedSenderCipher(
                    store = store,
                    certManager = certManager,
                    trustRootProvider = BuildConfigTrustRootProvider(),
                    dispatchers = dispatchers,
                )
            assertFailsWith<org.signal.libsignal.metadata.InvalidMetadataMessageException> {
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

    /**
     * Minimal in-test [MessagingServiceClient]. Only [getSenderCertificate]
     * is relevant to this suite — all other calls throw so accidental use in
     * a future test produces an unambiguous failure.
     */
    private class FakeMessagingClient(
        private val certificateBytes: ByteArray = ByteArray(0),
    ) : MessagingServiceClient {
        var getSenderCertificateCalls: Int = 0

        override suspend fun getSenderCertificate(request: SenderCertificateRequest): SenderCertificateResponse {
            getSenderCertificateCalls++
            return SenderCertificateResponse(
                certificate = certificateBytes,
                expiration = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7),
            )
        }

        override suspend fun sendMessage(request: SendMessageRequest): SendMessageResponse = error("not used")

        override suspend fun startDirectConversation(request: StartDirectConversationRequest): Conversation = error("not used")

        override fun messageStream(requests: Flow<ClientEvent>): Flow<ServerEvent> = emptyFlow()

        override fun syncMessages(request: SyncRequest): Flow<EncryptedEnvelope> = emptyFlow()

        override suspend fun ackMessages(request: AckMessagesRequest): AckMessagesResponse = error("not used")

        override suspend fun deleteMessage(request: DeleteMessageRequest): DeleteMessageResponse = error("not used")

        override suspend fun sendReceipt(request: ReceiptRequest): ReceiptResponse = error("not used")

        override suspend fun getConversations(request: GetConversationsRequest): GetConversationsResponse = error("not used")
    }
}
