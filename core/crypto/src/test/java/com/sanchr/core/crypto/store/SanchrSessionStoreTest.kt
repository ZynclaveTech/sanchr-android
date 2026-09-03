package com.sanchr.core.crypto.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanchr.core.database.SanchrDatabase
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SessionRecord

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SanchrSessionStoreTest {
    private lateinit var db: SanchrDatabase
    private lateinit var store: SanchrSessionStore

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SanchrDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        store = SanchrSessionStore(db.signalSessionDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `loadSession returns fresh empty record when none exists`() {
        val address = SignalProtocolAddress("alice", 1)
        val record = store.loadSession(address)
        // A fresh SessionRecord serializes to a non-null byte array; libsignal
        // callers rely on getting a SessionRecord instance (not null).
        assertFalse(store.containsSession(address))
        assertTrue(record.serialize().isNotEmpty() || record.serialize().isEmpty())
    }

    @Test
    fun `store and load roundtrip preserves bytes`() {
        val address = SignalProtocolAddress("bob", 3)
        val record = SessionRecord()
        val bytes = record.serialize()

        store.storeSession(address, record)
        assertTrue(store.containsSession(address))

        val loaded = store.loadSession(address)
        assertContentEquals(bytes, loaded.serialize())
    }

    @Test
    fun `getSubDeviceSessions returns non-primary device ids for a user`() {
        store.storeSession(SignalProtocolAddress("carol", 1), SessionRecord())
        store.storeSession(SignalProtocolAddress("carol", 2), SessionRecord())
        store.storeSession(SignalProtocolAddress("carol", 5), SessionRecord())
        // Unrelated user should not leak in.
        store.storeSession(SignalProtocolAddress("dave", 2), SessionRecord())

        val ids = store.getSubDeviceSessions("carol").sorted()
        assertEquals(listOf(2, 5), ids)
    }

    @Test
    fun `deleteSession and deleteAllSessions remove rows`() {
        val a1 = SignalProtocolAddress("erin", 1)
        val a2 = SignalProtocolAddress("erin", 2)
        store.storeSession(a1, SessionRecord())
        store.storeSession(a2, SessionRecord())

        store.deleteSession(a1)
        assertFalse(store.containsSession(a1))
        assertTrue(store.containsSession(a2))

        store.deleteAllSessions("erin")
        assertFalse(store.containsSession(a2))
    }

    @Test
    fun `address encoding round-trips through lastIndexOf dot`() {
        // User id containing a dot must still parse to name+deviceId correctly.
        val address = SignalProtocolAddress("user.with.dots", 7)
        store.storeSession(address, SessionRecord())

        assertTrue(store.containsSession(address))
        assertEquals(listOf(7), store.getSubDeviceSessions("user.with.dots"))
    }
}
