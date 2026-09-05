package com.sanchr.core.crypto.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanchr.core.crypto.InMemoryStagedIdentityStore
import com.sanchr.core.database.SanchrDatabase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.IdentityKeyStore

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SanchrIdentityKeyStoreTest {
    private lateinit var db: SanchrDatabase
    private lateinit var store: SanchrIdentityKeyStore

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SanchrDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        store =
            SanchrIdentityKeyStore(
                db.accountDao(),
                db.signalIdentityDao(),
                InMemoryStagedIdentityStore(ApplicationProvider.getApplicationContext()),
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `getIdentityKeyPair throws when account not initialized`() {
        assertFailsWith<IllegalStateException> { store.getIdentityKeyPair() }
        assertFailsWith<IllegalStateException> { store.getLocalRegistrationId() }
        assertFalse(store.hasIdentityKeyPair())
    }

    @Test
    fun `staged keypair survives until account initialization`() {
        val pair = IdentityKeyPair.generate()
        store.storeIdentityKeyPair(pair)
        store.storeLocalRegistrationId(4242)

        // Before initializeAccount: still reads from staging.
        assertTrue(store.hasIdentityKeyPair())
        assertEquals(4242, store.getLocalRegistrationId())
        assertTrue(pair.serialize().contentEquals(store.getIdentityKeyPair().serialize()))

        store.initializeAccount(userId = "u-1", deviceId = "d-1", phoneE164 = "+15551234567")

        // After initialize: DB has the row, staging cleared, reads still succeed.
        val account = db.accountDao().getCurrentBlocking()
        assertNotNull(account)
        assertEquals("u-1", account.userId)
        assertEquals(4242, account.registrationId)
        assertNotNull(account.identityPrivateKey)
        assertTrue(pair.serialize().contentEquals(store.getIdentityKeyPair().serialize()))
    }

    @Test
    fun `saveIdentity returns NEW_OR_UNCHANGED on first save and REPLACED on key change`() {
        val address = SignalProtocolAddress("peer-1", 2)
        val first = IdentityKeyPair.generate().publicKey
        val second = IdentityKeyPair.generate().publicKey

        assertEquals(
            IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED,
            store.saveIdentity(address, first),
        )
        assertEquals(
            IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED,
            store.saveIdentity(address, first),
        )
        assertEquals(
            IdentityKeyStore.IdentityChange.REPLACED_EXISTING,
            store.saveIdentity(address, second),
        )

        assertNotNull(store.getIdentity(address))
        assertTrue(store.getIdentity(address)!!.serialize().contentEquals(second.serialize()))
    }

    @Test
    fun `isTrustedIdentity is TOFU — unknown trusted, mismatch rejected`() {
        val address = SignalProtocolAddress("peer-2", 1)
        val a = IdentityKeyPair.generate().publicKey
        val b = IdentityKeyPair.generate().publicKey

        // Unknown → trusted (TOFU)
        assertTrue(store.isTrustedIdentity(address, a, IdentityKeyStore.Direction.RECEIVING))
        assertTrue(store.isTrustedIdentity(address, a, IdentityKeyStore.Direction.SENDING))

        store.saveIdentity(address, a)

        // Same key → trusted, different key → not trusted.
        assertTrue(store.isTrustedIdentity(address, a, IdentityKeyStore.Direction.SENDING))
        assertFalse(store.isTrustedIdentity(address, b, IdentityKeyStore.Direction.SENDING))
    }

    @Test
    fun `wipeAll clears accounts and identities`() {
        val pair = IdentityKeyPair.generate()
        store.storeIdentityKeyPair(pair)
        store.storeLocalRegistrationId(7)
        store.initializeAccount("u-x", "d-x", "+100")
        store.saveIdentity(SignalProtocolAddress("peer", 1), pair.publicKey)

        store.wipeAll()

        assertNull(db.accountDao().getCurrentBlocking())
        assertNull(db.signalIdentityDao().getBlocking("peer.1"))
        assertFalse(store.hasIdentityKeyPair())
    }

    @Test
    fun `a verification is recorded and read back`() {
        val address = SignalProtocolAddress("peer-1", 1)
        store.saveIdentity(address, IdentityKeyPair.generate().publicKey)

        assertNull(store.verifiedAtMillis(address), "a new identity starts unverified")

        store.markVerified(address, atMillis = 1_700_000_000_000)
        assertEquals(1_700_000_000_000, store.verifiedAtMillis(address))

        store.clearVerified(address)
        assertNull(store.verifiedAtMillis(address))
    }

    @Test
    fun `a changed identity key drops the verification`() {
        val address = SignalProtocolAddress("peer-2", 1)
        store.saveIdentity(address, IdentityKeyPair.generate().publicKey)
        store.markVerified(address, atMillis = 1_700_000_000_000)

        // Re-saving the same key is not a change: a routine re-fetch must not
        // silently un-verify a contact the user checked.
        store.saveIdentity(address, store.getIdentity(address)!!)
        assertEquals(1_700_000_000_000, store.verifiedAtMillis(address))

        store.saveIdentity(address, IdentityKeyPair.generate().publicKey)
        assertNull(store.verifiedAtMillis(address), "a verified badge must not outlive the key it vouched for")
    }

    @Test
    fun `marking an unknown identity verified records nothing`() {
        val address = SignalProtocolAddress("never-seen", 1)

        store.markVerified(address)

        assertNull(store.verifiedAtMillis(address))
    }

    @Test
    fun `a changed key blocks sending but still lets messages arrive`() {
        val address = SignalProtocolAddress("peer-3", 1)
        val original = IdentityKeyPair.generate().publicKey
        val replacement = IdentityKeyPair.generate().publicKey
        store.saveIdentity(address, original)

        assertTrue(store.isTrustedIdentity(address, original, IdentityKeyStore.Direction.SENDING))

        // Receiving stays open so a contact who reinstalled is not permanently
        // undecryptable; sending fails closed until the user reviews.
        assertTrue(store.isTrustedIdentity(address, replacement, IdentityKeyStore.Direction.RECEIVING))
        assertFalse(store.isTrustedIdentity(address, replacement, IdentityKeyStore.Direction.SENDING))
        assertTrue(store.hasPendingIdentityChange("peer-3"))
    }

    @Test
    fun `adopting the new key on receive does not erase the pending review`() {
        val address = SignalProtocolAddress("peer-4", 1)
        store.saveIdentity(address, IdentityKeyPair.generate().publicKey)
        val replacement = IdentityKeyPair.generate().publicKey

        store.isTrustedIdentity(address, replacement, IdentityKeyStore.Direction.RECEIVING)
        store.saveIdentity(address, replacement)

        assertTrue(store.hasPendingIdentityChange("peer-4"), "the save that adopts the key must not clear the warning")
        assertFalse(store.isTrustedIdentity(address, replacement, IdentityKeyStore.Direction.SENDING))
    }

    @Test
    fun `accepting the change unblocks sending without granting a verified badge`() {
        val address = SignalProtocolAddress("peer-5", 1)
        store.saveIdentity(address, IdentityKeyPair.generate().publicKey)
        val replacement = IdentityKeyPair.generate().publicKey
        store.isTrustedIdentity(address, replacement, IdentityKeyStore.Direction.RECEIVING)
        store.saveIdentity(address, replacement)

        store.acceptIdentityChange("peer-5")

        assertFalse(store.hasPendingIdentityChange("peer-5"))
        assertTrue(store.isTrustedIdentity(address, replacement, IdentityKeyStore.Direction.SENDING))
        assertNull(store.verifiedAtMillis(address), "acknowledging a change is weaker than comparing safety numbers")
    }

    @Test
    fun `comparing the new safety number clears the review as well`() {
        val address = SignalProtocolAddress("peer-6", 1)
        store.saveIdentity(address, IdentityKeyPair.generate().publicKey)
        val replacement = IdentityKeyPair.generate().publicKey
        store.isTrustedIdentity(address, replacement, IdentityKeyStore.Direction.RECEIVING)
        store.saveIdentity(address, replacement)

        store.markVerified(address, atMillis = 1_700_000_000_000)

        assertFalse(store.hasPendingIdentityChange("peer-6"))
        assertTrue(store.isTrustedIdentity(address, replacement, IdentityKeyStore.Direction.SENDING))
        assertEquals(1_700_000_000_000, store.verifiedAtMillis(address))
    }

    @Test
    fun `an unchanged key never blocks anything`() {
        val address = SignalProtocolAddress("peer-7", 1)
        val key = IdentityKeyPair.generate().publicKey
        store.saveIdentity(address, key)
        store.saveIdentity(address, key)

        assertFalse(store.hasPendingIdentityChange("peer-7"))
        assertTrue(store.isTrustedIdentity(address, key, IdentityKeyStore.Direction.SENDING))
    }

    @Test
    fun `a user id holding LIKE wildcards is not confused with another contact`() {
        val wildcard = SignalProtocolAddress("a_b%c", 1)
        val other = SignalProtocolAddress("axbyc", 1)
        store.saveIdentity(wildcard, IdentityKeyPair.generate().publicKey)
        store.saveIdentity(other, IdentityKeyPair.generate().publicKey)

        store.isTrustedIdentity(other, IdentityKeyPair.generate().publicKey, IdentityKeyStore.Direction.RECEIVING)

        assertTrue(store.hasPendingIdentityChange("axbyc"))
        assertFalse(store.hasPendingIdentityChange("a_b%c"), "underscore and percent must not match another user's id")
    }
}
