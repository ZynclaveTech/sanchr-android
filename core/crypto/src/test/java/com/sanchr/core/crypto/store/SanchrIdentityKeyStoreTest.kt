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
}
