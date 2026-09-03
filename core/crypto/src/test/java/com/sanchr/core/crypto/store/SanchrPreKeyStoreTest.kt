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
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.state.PreKeyRecord

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SanchrPreKeyStoreTest {
    private lateinit var db: SanchrDatabase
    private lateinit var store: SanchrPreKeyStore

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SanchrDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        store = SanchrPreKeyStore(db.signalPreKeyDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun prekey(id: Int): PreKeyRecord = PreKeyRecord(id, ECKeyPair.generate())

    @Test
    fun `loadPreKey throws InvalidKeyIdException when missing`() {
        assertFailsWith<InvalidKeyIdException> { store.loadPreKey(99) }
    }

    @Test
    fun `store load contains remove roundtrip`() {
        val record = prekey(42)
        store.storePreKey(42, record)
        assertTrue(store.containsPreKey(42))

        val loaded = store.loadPreKey(42)
        assertContentEquals(record.serialize(), loaded.serialize())

        store.removePreKey(42)
        assertFalse(store.containsPreKey(42))
        assertFailsWith<InvalidKeyIdException> { store.loadPreKey(42) }
    }

    @Test
    fun `getNextPreKeyId returns 1 when empty and max+1 otherwise`() {
        assertEquals(1, store.getNextPreKeyId())

        store.storePreKey(1, prekey(1))
        store.storePreKey(5, prekey(5))
        store.storePreKey(3, prekey(3))

        assertEquals(6, store.getNextPreKeyId())
    }

    @Test
    fun `upsert on same id replaces record`() {
        val first = prekey(7)
        val second = prekey(7)
        store.storePreKey(7, first)
        store.storePreKey(7, second)

        assertContentEquals(second.serialize(), store.loadPreKey(7).serialize())
    }

    @Test
    fun `wipeAll clears store`() {
        store.storePreKey(1, prekey(1))
        store.storePreKey(2, prekey(2))
        store.wipeAll()

        assertFalse(store.containsPreKey(1))
        assertFalse(store.containsPreKey(2))
        assertEquals(1, store.getNextPreKeyId())
    }
}
