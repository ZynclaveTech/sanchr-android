package com.sanchr.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Which blocks and unblocks the server has still to be told about. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class UserPreferencesPendingBlockSyncTest {
    private val prefs = UserPreferences(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `what is pending is kept, replaced by a later choice, and cleared once sent`() =
        runBlocking {
            prefs.clear()
            assertEquals(emptyMap(), prefs.pendingBlockSync.first())

            prefs.setPendingBlockSync("alice", true)
            prefs.setPendingBlockSync("bob", false)
            assertEquals(mapOf("alice" to true, "bob" to false), prefs.pendingBlockSync.first())

            // Unblocked before the block was ever sent: only the unblock is owed.
            prefs.setPendingBlockSync("alice", false)
            assertEquals(mapOf("alice" to false, "bob" to false), prefs.pendingBlockSync.first())

            prefs.setPendingBlockSync("alice", null)
            prefs.setPendingBlockSync("bob", null)
            assertEquals(emptyMap(), prefs.pendingBlockSync.first())
        }

    @Test
    fun `clearing something that was never pending changes nothing`() =
        runBlocking {
            prefs.clear()
            prefs.setPendingBlockSync("alice", true)

            prefs.setPendingBlockSync("nobody", null)

            assertEquals(mapOf("alice" to true), prefs.pendingBlockSync.first())
        }
}
