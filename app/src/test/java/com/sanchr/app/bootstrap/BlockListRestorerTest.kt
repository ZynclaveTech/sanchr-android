package com.sanchr.app.bootstrap

import com.sanchr.core.datastore.UserPreferences
import com.sanchr.domain.contacts.ContactRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** A new install takes the server's blocked list once, and never again. */
class BlockListRestorerTest {
    private val repository = mockk<ContactRepository>(relaxed = true)

    private fun restorer(alreadyRestored: Boolean): Pair<BlockListRestorer, UserPreferences> {
        val prefs = mockk<UserPreferences>(relaxed = true) { every { blockListRestored } returns flowOf(alreadyRestored) }
        return BlockListRestorer(repository, prefs) to prefs
    }

    @Test
    fun `a new install restores the list and remembers that it has`() =
        runTest {
            val (restorer, prefs) = restorer(alreadyRestored = false)
            coEvery { repository.restoreBlockedFromServer() } returns 3

            assertTrue(restorer.restoreOnce())

            coVerify(exactly = 1) { repository.restoreBlockedFromServer() }
            coVerify(exactly = 1) { prefs.setBlockListRestored(true) }
        }

    /**
     * After the first time the phone's record is the newer one. Taking the
     * server's list again would re-block someone whose unblock never reached
     * the server.
     */
    @Test
    fun `an install that already has it does not ask again`() =
        runTest {
            val (restorer, prefs) = restorer(alreadyRestored = true)

            assertTrue(restorer.restoreOnce())

            coVerify(exactly = 0) { repository.restoreBlockedFromServer() }
            coVerify(exactly = 0) { prefs.setBlockListRestored(any()) }
        }

    @Test
    fun `offline, it is left to try again at the next launch`() =
        runTest {
            val (restorer, prefs) = restorer(alreadyRestored = false)
            coEvery { repository.restoreBlockedFromServer() } throws RuntimeException("offline")

            assertFalse(restorer.restoreOnce())

            coVerify(exactly = 0) { prefs.setBlockListRestored(any()) }
        }
}
