package com.sanchr.core.crypto

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanchr.core.crypto.store.SanchrIdentityKeyStore
import com.sanchr.core.database.SanchrDatabase
import kotlin.test.assertEquals
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

/**
 * Verifies that [StagedIdentityStore] can round-trip a generated identity
 * across process restarts, and that [SanchrIdentityKeyStore] re-hydrates from
 * disk when constructed with no account row present.
 *
 * The real class uses AndroidKeyStore — Robolectric ships a working shadow
 * on sdk=33 that supports AES/GCM, which is sufficient for these tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class StagedIdentityStoreTest {
    private lateinit var context: Context
    private lateinit var db: SanchrDatabase
    private lateinit var store: StagedIdentityStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db =
            Room
                .inMemoryDatabaseBuilder(context, SanchrDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        store = InMemoryStagedIdentityStore(context)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
        db.close()
    }

    @Test
    fun `stage then loadStaged returns same keypair and registration id`() {
        val kp = IdentityKeyPair.generate()
        store.stage(kp, 1234)

        val loaded = store.loadStaged()
        assertNotNull(loaded)
        assertEquals(1234, loaded.registrationId)
        assertTrue(kp.serialize().contentEquals(loaded.keypair.serialize()))
    }

    @Test
    fun `clear removes the staged blob`() {
        store.stage(IdentityKeyPair.generate(), 42)
        store.clear()
        assertNull(store.loadStaged())
    }

    @Test
    fun `new store instance re-reads staged keys across process restart`() {
        val kp = IdentityKeyPair.generate()
        store.stage(kp, 9999)

        // Simulate process restart: drop the store ref, new instance, same context.
        val reborn = InMemoryStagedIdentityStore(context)
        val loaded = reborn.loadStaged()
        assertNotNull(loaded)
        assertEquals(9999, loaded.registrationId)
        assertTrue(kp.serialize().contentEquals(loaded.keypair.serialize()))
    }

    @Test
    fun `identity store re-hydrates from staged blob on construction`() {
        val kp = IdentityKeyPair.generate()
        // Stage before the identity store is constructed — mimics a crash
        // between storeIdentityKeyPair and initializeAccount.
        store.stage(kp, 7777)

        val identityStore =
            SanchrIdentityKeyStore(
                db.accountDao(),
                db.signalIdentityDao(),
                InMemoryStagedIdentityStore(context),
            )

        assertTrue(identityStore.hasIdentityKeyPair())
        assertEquals(7777, identityStore.getLocalRegistrationId())
        assertTrue(
            kp.serialize().contentEquals(identityStore.getIdentityKeyPair().serialize()),
        )
    }

    @Test
    fun `initializeAccount clears the staged blob`() {
        val identityStore =
            SanchrIdentityKeyStore(
                db.accountDao(),
                db.signalIdentityDao(),
                InMemoryStagedIdentityStore(context),
            )
        identityStore.storeIdentityKeyPair(IdentityKeyPair.generate())
        identityStore.storeLocalRegistrationId(4242)
        // Stagged blob must exist after storeIdentityKeyPair+storeLocalRegistrationId
        assertNotNull(InMemoryStagedIdentityStore(context).loadStaged())

        identityStore.initializeAccount("u-1", "1", "+100000")

        assertNull(InMemoryStagedIdentityStore(context).loadStaged())
        assertFalse(store.loadStaged() != null)
    }
}
