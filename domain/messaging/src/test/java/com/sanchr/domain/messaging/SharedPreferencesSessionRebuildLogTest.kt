package com.sanchr.domain.messaging

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SharedPreferencesSessionRebuildLogTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("session_rebuilds", Context.MODE_PRIVATE)

    @Test
    fun `what one instance records the next one reads`() {
        SharedPreferencesSessionRebuildLog(context).record("alice.1", startedAt = 5_000, forgetBefore = 0)

        val reopened = SharedPreferencesSessionRebuildLog(context)
        assertEquals(5_000, reopened.lastStarted("alice.1"))
        assertNull(reopened.lastStarted("alice.2"))
        assertNull(reopened.lastStarted("bob.1"))
    }

    @Test
    fun `the throttle holds across instances over the real file`() {
        var clock = 1_000_000L
        val before = SessionRebuildThrottle(SharedPreferencesSessionRebuildLog(context), 600_000) { clock }
        assertTrue(before.tryStart("alice", 1))

        clock += 60_000
        val after = SessionRebuildThrottle(SharedPreferencesSessionRebuildLog(context), 600_000) { clock }
        assertFalse(after.tryStart("alice", 1))

        clock += 540_000
        assertTrue(after.tryStart("alice", 1))
    }

    @Test
    fun `the file does not name who failed to decrypt`() {
        val log = SharedPreferencesSessionRebuildLog(context)
        log.record("3f1c9a52-7d0e-4b6a-9c11-2e8f5a7d4b90.1", startedAt = 5_000, forgetBefore = 0)

        val written = prefs.all.entries.joinToString { "${it.key}=${it.value}" }
        assertFalse("3f1c9a52" in written, written)
    }

    @Test
    fun `an entry past its cooldown is removed, and the salt is not`() {
        val log = SharedPreferencesSessionRebuildLog(context)
        log.record("alice.1", startedAt = 5_000, forgetBefore = 0)
        log.record("bob.1", startedAt = 700_000, forgetBefore = 100_000)

        assertNull(log.lastStarted("alice.1"))
        assertEquals(700_000, log.lastStarted("bob.1"))
        // Salt plus the one live entry. Losing the salt would orphan it.
        assertEquals(2, prefs.all.size)
        assertEquals(700_000, SharedPreferencesSessionRebuildLog(context).lastStarted("bob.1"))
    }

    @Test
    fun `clearing leaves nothing in the file`() {
        val log = SharedPreferencesSessionRebuildLog(context)
        log.record("alice.1", startedAt = 5_000, forgetBefore = 0)

        log.clear()

        assertTrue(prefs.all.isEmpty())
        assertNull(SharedPreferencesSessionRebuildLog(context).lastStarted("alice.1"))
    }
}
