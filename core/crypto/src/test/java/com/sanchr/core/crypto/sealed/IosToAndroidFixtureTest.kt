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
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

/**
 * An envelope written by the iOS app's build of libsignal, opened by this
 * app's real stores and [TypedEnvelopeCipher].
 *
 * `interop/ios-typed-v1.json` is produced by the iOS repo's
 * `CrossPlatformFixtureTests` and copied here; see that test for how to
 * regenerate it. `AndroidToIosFixtureTest` is the other direction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class IosToAndroidFixtureTest {
    private lateinit var db: SanchrDatabase
    private lateinit var store: SanchrSignalProtocolStore
    private lateinit var cipher: TypedEnvelopeCipher
    private lateinit var fixture: JSONObject
    private lateinit var sender: JSONObject

    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }

    private fun bytes(
        json: JSONObject,
        key: String,
    ): ByteArray = Base64.getDecoder().decode(json.getString(key))

    /** Reads the sender the payload names, as the receive path does. */
    private val declaredSender: (ByteArray) -> DeclaredSender? = { plaintext ->
        val payload = JSONObject(String(plaintext))
        DeclaredSender(payload.getString("sender_user_id"), payload.getInt("sender_device_id"))
    }

    @Before
    fun setUp() {
        fixture = JSONObject(checkNotNull(javaClass.getResource("/interop/ios-typed-v1.json")) { "missing fixture" }.readText())
        sender = fixture.getJSONObject("sender")
        val recipient = fixture.getJSONObject("recipient")

        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SanchrDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val identityStore =
            SanchrIdentityKeyStore(
                db.accountDao(),
                db.signalIdentityDao(),
                com.sanchr.core.crypto
                    .InMemoryStagedIdentityStore(context),
            )
        val sessionStore = SanchrSessionStore(db.signalSessionDao())
        store =
            SanchrSignalProtocolStore(
                identityStore,
                SanchrPreKeyStore(db.signalPreKeyDao()),
                SanchrSignedPreKeyStore(db.signalSignedPreKeyDao()),
                sessionStore,
                SanchrSenderKeyStore(context),
                SanchrKyberPreKeyStore(context),
            )
        identityStore.storeIdentityKeyPair(IdentityKeyPair(bytes(recipient, "identityKeyPair")))
        identityStore.storeLocalRegistrationId(recipient.getInt("registrationId"))
        identityStore.initializeAccount(
            userId = recipient.getString("userId"),
            deviceId = recipient.getInt("deviceId").toString(),
            phoneE164 = "+10000000000",
        )
        store.storePreKey(recipient.getInt("preKeyId"), PreKeyRecord(bytes(recipient, "preKey")))
        store.storeSignedPreKey(recipient.getInt("signedPreKeyId"), SignedPreKeyRecord(bytes(recipient, "signedPreKey")))
        store.storeKyberPreKey(recipient.getInt("kyberPreKeyId"), KyberPreKeyRecord(bytes(recipient, "kyberPreKey")))
        cipher = TypedEnvelopeCipher(store, sessionStore, identityStore, dispatchers)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `an envelope written by iOS is opened here`() =
        runTest {
            val envelope = bytes(fixture, "envelope")
            assertTrue(TypedEnvelope.isTyped(envelope))
            val published = IdentityKey(bytes(sender, "identityKey"), 0)

            val opened = cipher.decryptSealed(envelope, declaredSender) { _, _ -> published }

            assertEquals(sender.getString("userId"), opened.senderUserId)
            assertEquals(sender.getInt("deviceId"), opened.senderDeviceId)
            // Exactly the payload: iOS does not pad, and nothing is stripped.
            assertContentEquals(bytes(fixture, "plaintext"), opened.plaintext)
            val payload = JSONObject(String(opened.plaintext))
            assertEquals("fixture-conversation", payload.getString("conversation_id"))
            assertEquals("text", payload.getString("content_type"))
            assertEquals("hello from ios", String(Base64.getDecoder().decode(payload.getString("content"))))
            assertTrue(store.containsSession(SignalProtocolAddress(opened.senderUserId, opened.senderDeviceId)))
        }

    @Test
    fun `the same envelope is refused when the server publishes a different key for its sender`() =
        runTest {
            val someoneElse = IdentityKeyPair.generate().publicKey

            assertFailsWith<UntrustedIdentityException> {
                cipher.decryptSealed(bytes(fixture, "envelope"), declaredSender) { _, _ -> someoneElse }
            }
            assertFalse(store.containsSession(SignalProtocolAddress(sender.getString("userId"), sender.getInt("deviceId"))))
        }
}
