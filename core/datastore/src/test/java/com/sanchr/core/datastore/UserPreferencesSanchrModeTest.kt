package com.sanchr.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sanchr Mode was a switch that changed nothing. These pin what it has to
 * change — and that turning it off again puts things back as they were.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class UserPreferencesSanchrModeTest {
    private val prefs = UserPreferences(ApplicationProvider.getApplicationContext<Context>())

    @After
    fun tearDown() = runBlocking { prefs.clear() }

    private data class Allowed(
        val receipts: Boolean,
        val typing: Boolean,
        val presence: Boolean,
        val shielded: Boolean,
    )

    private fun allowed() =
        runBlocking {
            Allowed(
                receipts = prefs.canSendReadReceipts.first(),
                typing = prefs.canSendTypingIndicators.first(),
                presence = prefs.canSendPresence.first(),
                shielded = prefs.screenSecurityActive.first(),
            )
        }

    @Test
    fun `it is off until turned on, and everything follows its own toggle`() =
        runBlocking {
            assertFalse(prefs.sanchrModeEnabled.first())
            assertEquals(Allowed(receipts = true, typing = true, presence = true, shielded = true), allowed())

            prefs.setReadReceiptsEnabled(false)
            prefs.setTypingIndicatorsEnabled(false)
            prefs.setScreenshotProtectionEnabled(false)

            assertEquals(Allowed(receipts = false, typing = false, presence = true, shielded = false), allowed())
        }

    @Test
    fun `turned on, nothing is sent and the screen is shielded, whatever the toggles say`() =
        runBlocking {
            prefs.setScreenshotProtectionEnabled(false)

            prefs.setSanchrModeEnabled(true)

            assertEquals(Allowed(receipts = false, typing = false, presence = false, shielded = true), allowed())
        }

    @Test
    fun `the toggles keep their own values underneath it`() =
        runBlocking {
            prefs.setReadReceiptsEnabled(true)
            prefs.setTypingIndicatorsEnabled(false)
            prefs.setScreenshotProtectionEnabled(false)
            prefs.setSanchrModeEnabled(true)

            // What the settings screens show is unchanged.
            assertTrue(prefs.readReceiptsEnabled.first())
            assertFalse(prefs.typingIndicatorsEnabled.first())
            assertFalse(prefs.screenshotProtectionEnabled.first())

            prefs.setSanchrModeEnabled(false)

            assertEquals(Allowed(receipts = true, typing = false, presence = true, shielded = false), allowed())
        }

    @Test
    fun `removing the account turns it off with everything else`() =
        runBlocking {
            prefs.setSanchrModeEnabled(true)

            prefs.clear()

            assertFalse(prefs.sanchrModeEnabled.first())
        }
}
