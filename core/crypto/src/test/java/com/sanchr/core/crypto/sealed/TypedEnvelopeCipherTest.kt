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
import java.io.IOException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore

/**
 * Reading the envelope iOS sends.
 *
 * This device is "me", with the app's real stores. Everyone else is an
 * in-memory libsignal peer that frames its messages the way the iOS app does:
 * a type byte, then the Signal message, unpadded.
 *
 * The cases that matter most are the ones about *who* a message is from. The
 * envelope names no sender, and the first message of a session will open
 * under any address it is tried against — so a careless reader attributes a
 * stranger's message to an existing contact and overwrites that contact's
 * session with the stranger's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TypedEnvelopeCipherTest {
    private lateinit var db: SanchrDatabase
    private lateinit var store: SanchrSignalProtocolStore
    private lateinit var sessionStore: SanchrSessionStore
    private lateinit var identityStore: SanchrIdentityKeyStore
    private lateinit var cipher: TypedEnvelopeCipher
    private lateinit var myIdentity: IdentityKeyPair

    private val me = "00000000-0000-0000-0000-00000000000a"
    private var nextKeyId = 100
    private var publishedLookups = 0

    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }

    /** A peer on the other platform. */
    private inner class Peer(
        val userId: String,
        val deviceId: Int = 1,
    ) {
        val identity: IdentityKeyPair = IdentityKeyPair.generate()
        val signal = InMemorySignalProtocolStore(identity, 1234)
        private val myAddress = SignalProtocolAddress(me, 1)
        val address get() = SignalProtocolAddress(userId, deviceId)

        /** Starts a session with me from a fresh bundle of my keys, as fetching one from the server would. */
        fun startSession() = SessionBuilder(signal, myAddress).process(myBundle())

        /** A message framed as iOS frames it. [claiming] is the sender the payload names. */
        fun send(
            text: String,
            claiming: Pair<String, Int>? = userId to deviceId,
        ): ByteArray {
            val payload = payload(text, claiming)
            val message = SessionCipher(signal, myAddress).encrypt(payload)
            val type = if (message.type == CiphertextMessage.PREKEY_TYPE) TypedEnvelope.PREKEY else TypedEnvelope.SIGNAL
            return byteArrayOf(type) + message.serialize()
        }

        /** Reads a reply from me, which is what moves this peer off first-message framing. */
        fun receive(reply: CiphertextMessage) {
            val session = SessionCipher(signal, myAddress)
            if (reply.type == CiphertextMessage.PREKEY_TYPE) {
                session.decrypt(PreKeySignalMessage(reply.serialize()))
            } else {
                session.decrypt(SignalMessage(reply.serialize()))
            }
        }
    }

    private fun payload(
        text: String,
        claiming: Pair<String, Int>?,
    ): ByteArray {
        val sender = claiming?.let { ""","sender_user_id":"${it.first}","sender_device_id":${it.second}""" }.orEmpty()
        return """{"v":1,"text":"$text"$sender}""".toByteArray()
    }

    /** What the receive path does with a payload: reads the sender it names. */
    private val declaredSender: (ByteArray) -> DeclaredSender? = { bytes ->
        val json = String(bytes)
        val user = Regex(""""sender_user_id":"([^"]+)"""").find(json)?.groupValues?.get(1)
        val device =
            Regex(""""sender_device_id":(\d+)""")
                .find(json)
                ?.groupValues
                ?.get(1)
                ?.toInt()
        if (user != null && device != null) DeclaredSender(user, device) else null
    }

    /** A server that publishes exactly these identities. */
    private fun publishing(vararg peers: Peer): suspend (String, Int) -> IdentityKey? =
        { userId, _ ->
            publishedLookups++
            peers.firstOrNull { it.userId == userId }?.identity?.publicKey
        }

    private val nobody: suspend (String, Int) -> IdentityKey? = { _, _ ->
        publishedLookups++
        null
    }

    private fun myBundle(): PreKeyBundle {
        val now = System.currentTimeMillis()
        val oneTimeId = nextKeyId++
        val signedId = nextKeyId++
        val kyberId = nextKeyId++
        val oneTime = ECKeyPair.generate()
        val signed = ECKeyPair.generate()
        val kyber = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val signedSignature = myIdentity.privateKey.calculateSignature(signed.publicKey.serialize())
        val kyberSignature = myIdentity.privateKey.calculateSignature(kyber.publicKey.serialize())
        store.storePreKey(oneTimeId, PreKeyRecord(oneTimeId, oneTime))
        store.storeSignedPreKey(signedId, SignedPreKeyRecord(signedId, now, signed, signedSignature))
        store.storeKyberPreKey(kyberId, KyberPreKeyRecord(kyberId, now, kyber, kyberSignature))
        return PreKeyBundle(
            4242,
            1,
            oneTimeId,
            oneTime.publicKey,
            signedId,
            signed.publicKey,
            signedSignature,
            myIdentity.publicKey,
            kyberId,
            kyber.publicKey,
            kyberSignature,
        )
    }

    /** Makes [peer] an existing contact: a session both ways, and their key on record. */
    private suspend fun befriend(peer: Peer) {
        peer.startSession()
        cipher.decryptSealed(peer.send("hello"), declaredSender, publishing(peer))
        peer.receive(SessionCipher(store, peer.address).encrypt("hi back".toByteArray()))
        publishedLookups = 0
    }

    private fun scratchLeftBehind(): Boolean =
        sessionStore.allAddresses().any { it.name.startsWith("sealedscratch:") } ||
            db.signalIdentityDao().getForUserBlocking("sealedscratch:%").isNotEmpty()

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SanchrDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        identityStore =
            SanchrIdentityKeyStore(
                db.accountDao(),
                db.signalIdentityDao(),
                com.sanchr.core.crypto
                    .InMemoryStagedIdentityStore(context),
            )
        sessionStore = SanchrSessionStore(db.signalSessionDao())
        store =
            SanchrSignalProtocolStore(
                identityStore,
                SanchrPreKeyStore(db.signalPreKeyDao()),
                SanchrSignedPreKeyStore(db.signalSignedPreKeyDao()),
                sessionStore,
                SanchrSenderKeyStore(context),
                SanchrKyberPreKeyStore(context),
            )
        myIdentity = IdentityKeyPair.generate()
        identityStore.storeIdentityKeyPair(myIdentity)
        identityStore.storeLocalRegistrationId(4242)
        identityStore.initializeAccount(userId = me, deviceId = "1", phoneE164 = "+10000000000")
        cipher = TypedEnvelopeCipher(store, sessionStore, identityStore, dispatchers)
    }

    @After
    fun tearDown() = db.close()

    // ------------------------------------------------------------------
    // Telling the formats apart
    // ------------------------------------------------------------------

    @Test
    fun `only an envelope that starts with a type byte is this format`() {
        assertTrue(TypedEnvelope.isTyped(byteArrayOf(0x01, 0x33, 0x00)))
        assertTrue(TypedEnvelope.isTyped(byteArrayOf(0x02, 0x33, 0x00)))
        // A sealed-sender envelope starts with its version, a bare Signal
        // message with its own.
        assertFalse(TypedEnvelope.isTyped(byteArrayOf(0x11, 0x0a, 0x00)))
        assertFalse(TypedEnvelope.isTyped(byteArrayOf(0x22, 0x0a, 0x00)))
        assertFalse(TypedEnvelope.isTyped(byteArrayOf(0x33, 0x0a, 0x00)))
        assertFalse(TypedEnvelope.isTyped(byteArrayOf(0x01)))
        assertFalse(TypedEnvelope.isTyped(ByteArray(0)))
    }

    // ------------------------------------------------------------------
    // The first message of a session
    // ------------------------------------------------------------------

    @Test
    fun `a first message from someone the server vouches for is read and its session placed under their address`() =
        runTest {
            val alice = Peer("alice")
            alice.startSession()

            val opened = cipher.decryptSealed(alice.send("hello"), declaredSender, publishing(alice))

            assertEquals("alice", opened.senderUserId)
            assertEquals(1, opened.senderDeviceId)
            // Unpadded, as iOS sends it: nothing is stripped from the end.
            assertContentEquals(payload("hello", "alice" to 1), opened.plaintext)
            assertTrue(store.containsSession(alice.address))
            assertEquals(alice.identity.publicKey, store.getIdentity(alice.address))
            assertFalse(scratchLeftBehind())
        }

    @Test
    fun `the session is placed under the device the payload names`() =
        runTest {
            val tablet = Peer("alice", deviceId = 2)
            tablet.startSession()

            val opened = cipher.decryptSealed(tablet.send("from the tablet"), declaredSender, publishing(tablet))

            assertEquals(2, opened.senderDeviceId)
            assertTrue(store.containsSession(SignalProtocolAddress("alice", 2)))
            assertFalse(store.containsSession(SignalProtocolAddress("alice", 1)))
        }

    @Test
    fun `a key already on record for the sender needs no question to the server`() =
        runTest {
            val alice = Peer("alice")
            store.saveIdentity(alice.address, alice.identity.publicKey)
            alice.startSession()

            cipher.decryptSealed(alice.send("hello"), declaredSender, nobody)

            assertEquals(0, publishedLookups)
            assertTrue(store.containsSession(alice.address))
        }

    @Test
    fun `a stranger cannot introduce themselves as someone else`() =
        runTest {
            val carol = Peer("carol")
            val mallory = Peer("mallory")
            mallory.startSession()

            // Mallory's own key, Carol's name. The server publishes Carol's
            // real key, which is not the one this message was sent with.
            val forged = mallory.send("it's carol, new phone", claiming = "carol" to 1)

            assertFailsWith<UntrustedIdentityException> { cipher.decryptSealed(forged, declaredSender, publishing(carol, mallory)) }
            assertFalse(store.containsSession(carol.address))
            assertNull(store.getIdentity(carol.address))
            assertFalse(scratchLeftBehind())
        }

    @Test
    fun `a forged first message leaves an existing contact's session and key exactly as they were`() =
        runTest {
            val carol = Peer("carol")
            befriend(carol)
            val sessionBefore = store.loadSession(carol.address).serialize()
            val mallory = Peer("mallory")
            mallory.startSession()

            val forged = mallory.send("it's carol, new phone", claiming = "carol" to 1)
            assertFailsWith<UntrustedIdentityException> { cipher.decryptSealed(forged, declaredSender, publishing(carol, mallory)) }

            assertContentEquals(sessionBefore, store.loadSession(carol.address).serialize())
            assertEquals(carol.identity.publicKey, store.getIdentity(carol.address))
            // Not flagged as "safety number changed" either.
            assertFalse(identityStore.hasPendingIdentityChange("carol"))
            // And Carol's real messages still arrive.
            assertEquals("carol", cipher.decryptSealed(carol.send("still me"), declaredSender, nobody).senderUserId)
        }

    @Test
    fun `a stranger's first message does not disturb the contacts already here`() =
        runTest {
            // The hazard of trying a first message against every session: it
            // opens under whichever is tried first and replaces it.
            val carol = Peer("carol")
            val erin = Peer("erin")
            befriend(carol)
            befriend(erin)
            val carolBefore = store.loadSession(carol.address).serialize()
            val erinBefore = store.loadSession(erin.address).serialize()
            val dave = Peer("dave")
            dave.startSession()

            val opened = cipher.decryptSealed(dave.send("hi, I'm new"), declaredSender, publishing(dave))

            assertEquals("dave", opened.senderUserId)
            assertContentEquals(carolBefore, store.loadSession(carol.address).serialize())
            assertContentEquals(erinBefore, store.loadSession(erin.address).serialize())
            assertFalse(identityStore.hasPendingIdentityChange("carol"))
            assertFalse(identityStore.hasPendingIdentityChange("erin"))
        }

    @Test
    fun `a first message that does not say who it is from is refused`() =
        runTest {
            val alice = Peer("alice")
            alice.startSession()

            assertFailsWith<InvalidMessageException> {
                cipher.decryptSealed(alice.send("anonymous", claiming = null), declaredSender, publishing(alice))
            }
            assertFalse(store.containsSession(alice.address))
            assertFalse(scratchLeftBehind())
        }

    @Test
    fun `a contact whose key has changed is read, and the change is left for the user to review`() =
        runTest {
            val before = Peer("alice")
            befriend(before)
            // The same person after a reinstall: same id, new key, and the
            // server now publishes the new one.
            val after = Peer("alice")
            after.startSession()

            val opened = cipher.decryptSealed(after.send("new phone"), declaredSender, publishing(after))

            assertEquals("alice", opened.senderUserId)
            assertEquals(after.identity.publicKey, store.getIdentity(after.address))
            assertTrue(identityStore.hasPendingIdentityChange("alice"))
        }

    @Test
    fun `a changed key that is still awaiting review does not vouch for itself`() =
        runTest {
            val before = Peer("alice")
            befriend(before)
            val after = Peer("alice")
            after.startSession()
            // Adopted on receive, flagged for review.
            cipher.decryptSealed(after.send("new phone"), declaredSender, publishing(after))
            assertTrue(identityStore.hasPendingIdentityChange("alice"))
            publishedLookups = 0

            // Seen again: it is on record, but only as a pending change, so
            // the server is asked again rather than the record being trusted.
            after.startSession()
            cipher.decryptSealed(after.send("again"), declaredSender, publishing(after))

            assertEquals(1, publishedLookups)
        }

    @Test
    fun `when the server cannot be asked, the claim is not believed`() =
        runTest {
            val alice = Peer("alice")
            alice.startSession()
            var asked = 0
            val unreachable: suspend (String, Int) -> IdentityKey? = { _, _ ->
                asked++
                throw IOException("offline")
            }

            assertFailsWith<UntrustedIdentityException> { cipher.decryptSealed(alice.send("hello"), declaredSender, unreachable) }

            // Asked more than once first: the message cannot be opened again.
            assertEquals(3, asked)
            assertFalse(store.containsSession(alice.address))
            assertFalse(scratchLeftBehind())
        }

    // ------------------------------------------------------------------
    // Messages inside an established session
    // ------------------------------------------------------------------

    @Test
    fun `a later message is found among several sessions and attributed to the right one`() =
        runTest {
            val carol = Peer("carol")
            val erin = Peer("erin")
            val alice = Peer("alice", deviceId = 3)
            listOf(carol, erin, alice).forEach { befriend(it) }

            val envelope = alice.send("second message")
            assertEquals(TypedEnvelope.SIGNAL, envelope[0])
            val opened = cipher.decryptSealed(envelope, declaredSender, nobody)

            assertEquals("alice", opened.senderUserId)
            assertEquals(3, opened.senderDeviceId)
            assertContentEquals(payload("second message", "alice" to 3), opened.plaintext)
            assertEquals(0, publishedLookups)
        }

    @Test
    fun `the sender named inside a later message is not what attributes it`() =
        runTest {
            val carol = Peer("carol")
            val erin = Peer("erin")
            befriend(carol)
            befriend(erin)

            // Erin's session, Carol's name in the payload. The session that
            // authenticated it is the answer.
            val opened = cipher.decryptSealed(erin.send("I am carol", claiming = "carol" to 1), declaredSender, nobody)

            assertEquals("erin", opened.senderUserId)
        }

    @Test
    fun `a replayed message is recognised as a duplicate`() =
        runTest {
            val alice = Peer("alice")
            befriend(alice)
            val envelope = alice.send("once")
            cipher.decryptSealed(envelope, declaredSender, nobody)

            assertFailsWith<DuplicateMessageException> { cipher.decryptSealed(envelope, declaredSender, nobody) }
        }

    @Test
    fun `a later message from a session this device does not hold is refused and changes nothing`() =
        runTest {
            val carol = Peer("carol")
            befriend(carol)
            val carolBefore = store.loadSession(carol.address).serialize()
            // A session with someone else entirely, framed as a later message.
            val elsewhere = Peer("zed")
            val other = InMemorySignalProtocolStore(IdentityKeyPair.generate(), 9)
            val otherAddress = SignalProtocolAddress("other", 1)
            val signed = ECKeyPair.generate()
            val kyber = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
            val otherIdentity = other.identityKeyPair
            SessionBuilder(elsewhere.signal, otherAddress).process(
                PreKeyBundle(
                    9,
                    1,
                    PreKeyBundle.NULL_PRE_KEY_ID,
                    null,
                    1,
                    signed.publicKey,
                    otherIdentity.privateKey.calculateSignature(signed.publicKey.serialize()),
                    otherIdentity.publicKey,
                    2,
                    kyber.publicKey,
                    otherIdentity.privateKey.calculateSignature(kyber.publicKey.serialize()),
                ),
            )
            val foreign = SessionCipher(elsewhere.signal, otherAddress).encrypt("not for me".toByteArray())
            val embedded = PreKeySignalMessage(foreign.serialize()).whisperMessage

            assertFailsWith<InvalidMessageException> {
                cipher.decryptSealed(byteArrayOf(TypedEnvelope.SIGNAL) + embedded.serialize(), declaredSender, nobody)
            }
            assertContentEquals(carolBefore, store.loadSession(carol.address).serialize())
        }

    // ------------------------------------------------------------------
    // With a sender the server named
    // ------------------------------------------------------------------

    @Test
    fun `an envelope with a named sender is opened with that sender's session, first message and later ones`() =
        runTest {
            val alice = Peer("alice")
            alice.startSession()

            val first = cipher.decryptFrom(alice.send("one"), "alice", 1)
            alice.receive(SessionCipher(store, alice.address).encrypt("reply".toByteArray()))
            val later = cipher.decryptFrom(alice.send("two"), "alice", 1)

            assertContentEquals(payload("one", "alice" to 1), first)
            assertContentEquals(payload("two", "alice" to 1), later)
        }
}
