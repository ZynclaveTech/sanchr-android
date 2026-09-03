package com.sanchr.core.crypto.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanchr.core.database.SanchrDatabase
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SanchrSignedPreKeyStoreTest {
    private lateinit var db: SanchrDatabase
    private lateinit var store: SanchrSignedPreKeyStore
    private lateinit var identity: IdentityKeyPair

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SanchrDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        store = SanchrSignedPreKeyStore(db.signalSignedPreKeyDao())
        identity = IdentityKeyPair.generate()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun signedPreKey(
        id: Int,
        timestamp: Long = System.currentTimeMillis(),
    ): SignedPreKeyRecord {
        val pair = ECKeyPair.generate()
        val signature = identity.privateKey.calculateSignature(pair.publicKey.serialize())
        return SignedPreKeyRecord(id, timestamp, pair, signature)
    }

    @Test
    fun `loadSignedPreKey throws InvalidKeyIdException when missing`() {
        assertFailsWith<InvalidKeyIdException> { store.loadSignedPreKey(100) }
    }

    @Test
    fun `store load remove roundtrip`() {
        val record = signedPreKey(1)
        store.storeSignedPreKey(1, record)
        assertTrue(store.containsSignedPreKey(1))

        val loaded = store.loadSignedPreKey(1)
        assertContentEquals(record.serialize(), loaded.serialize())

        store.removeSignedPreKey(1)
        assertFalse(store.containsSignedPreKey(1))
    }

    @Test
    fun `loadSignedPreKeys returns all stored records`() {
        val r1 = signedPreKey(1)
        val r2 = signedPreKey(2)
        val r3 = signedPreKey(3)
        store.storeSignedPreKey(1, r1)
        store.storeSignedPreKey(2, r2)
        store.storeSignedPreKey(3, r3)

        val ids = store.loadSignedPreKeys().map { it.id }.sorted()
        assertEquals(listOf(1, 2, 3), ids)
    }

    @Test
    fun `getNextSignedPreKeyId returns 1 when empty and max+1 otherwise`() {
        assertEquals(1, store.getNextSignedPreKeyId())
        store.storeSignedPreKey(1, signedPreKey(1))
        store.storeSignedPreKey(4, signedPreKey(4))
        assertEquals(5, store.getNextSignedPreKeyId())
    }

    @Test
    fun `wipeAll clears the store`() {
        store.storeSignedPreKey(1, signedPreKey(1))
        store.storeSignedPreKey(2, signedPreKey(2))
        store.wipeAll()
        assertEquals(emptyList(), store.loadSignedPreKeys().map { it.id })
    }
}
