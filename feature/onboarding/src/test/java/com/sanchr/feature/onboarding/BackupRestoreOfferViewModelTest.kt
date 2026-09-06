package com.sanchr.feature.onboarding

import com.sanchr.sync.backup.BackupRestoreOutcome
import com.sanchr.sync.backup.ChatBackupManager
import com.sanchr.sync.backup.RestorableBackup
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The offer that decides whether a reinstalling user gets their history back.
 *
 * The failure that matters is not a wrong pixel: it is showing "restore your
 * chats" to someone with no backup, or silently skipping the offer for
 * someone who has one. Both leave the user believing something untrue about
 * their own data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreOfferViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val backupManager = mockk<ChatBackupManager>()

    private val backup =
        RestorableBackup(backupId = "b1", createdAtMillis = 1_700_000_000_000, sizeBytes = 4096)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `an account with a backup is offered it`() =
        runTest(dispatcher) {
            coEvery { backupManager.findRestorableBackup() } returns backup

            val viewModel = BackupRestoreOfferViewModel(backupManager)
            advanceUntilIdle()

            val state = assertIs<BackupRestoreOfferState.Found>(viewModel.state.value)
            assertEquals(backup, state.backup)
        }

    @Test
    fun `an account with no backup is not offered one`() =
        runTest(dispatcher) {
            coEvery { backupManager.findRestorableBackup() } returns null

            val viewModel = BackupRestoreOfferViewModel(backupManager)
            advanceUntilIdle()

            assertEquals(BackupRestoreOfferState.Nothing, viewModel.state.value)
        }

    /**
     * The probe swallows its own failures and answers null, so an offline
     * launch skips the offer rather than promising history that cannot
     * arrive. Restore is still reachable from Settings afterwards.
     */
    @Test
    fun `a failed check skips the offer rather than showing a broken one`() =
        runTest(dispatcher) {
            coEvery { backupManager.findRestorableBackup() } returns null

            val viewModel = BackupRestoreOfferViewModel(backupManager)
            advanceUntilIdle()

            assertIs<BackupRestoreOfferState.Nothing>(viewModel.state.value)
        }

    @Test
    fun `restoring reports the backup it came from`() =
        runTest(dispatcher) {
            coEvery { backupManager.findRestorableBackup() } returns backup
            coEvery { backupManager.restoreLatestBackup(any()) } returns
                BackupRestoreOutcome(
                    lineageId = "l1",
                    formatVersion = 1,
                    backupAtMillis = 1_700_000_000_000,
                    contentHash = "hash",
                )

            val viewModel = BackupRestoreOfferViewModel(backupManager)
            advanceUntilIdle()
            viewModel.onRecoveryKeyChanged("  a-recovery-key  ")
            viewModel.restore()
            advanceUntilIdle()

            val state = assertIs<BackupRestoreOfferState.Restored>(viewModel.state.value)
            assertEquals(1_700_000_000_000, state.backupAtMillis)
            // Trimmed: a key pasted from a password manager routinely carries
            // whitespace, and rejecting it for that would look like a wrong key.
            coVerify { backupManager.restoreLatestBackup("a-recovery-key") }
        }

    /**
     * A wrong key is the likely failure, and it must not clear the field —
     * retyping a long key to fix one character is how people give up.
     */
    @Test
    fun `a failed restore keeps the key and explains itself`() =
        runTest(dispatcher) {
            coEvery { backupManager.findRestorableBackup() } returns backup
            coEvery { backupManager.restoreLatestBackup(any()) } throws IllegalStateException("Backup HMAC verification failed")

            val viewModel = BackupRestoreOfferViewModel(backupManager)
            advanceUntilIdle()
            viewModel.onRecoveryKeyChanged("wrong-key")
            viewModel.restore()
            advanceUntilIdle()

            val state = assertIs<BackupRestoreOfferState.Found>(viewModel.state.value)
            assertEquals("wrong-key", state.recoveryKey)
            assertEquals("Backup HMAC verification failed", state.error)
            assertTrue(!state.isRestoring)
        }

    @Test
    fun `typing again clears the previous error`() =
        runTest(dispatcher) {
            coEvery { backupManager.findRestorableBackup() } returns backup
            coEvery { backupManager.restoreLatestBackup(any()) } throws IllegalStateException("nope")

            val viewModel = BackupRestoreOfferViewModel(backupManager)
            advanceUntilIdle()
            viewModel.onRecoveryKeyChanged("bad")
            viewModel.restore()
            advanceUntilIdle()
            viewModel.onRecoveryKeyChanged("better")

            val state = assertIs<BackupRestoreOfferState.Found>(viewModel.state.value)
            assertEquals(null, state.error)
        }

    @Test
    fun `a second tap while restoring does not start a second restore`() =
        runTest(dispatcher) {
            coEvery { backupManager.findRestorableBackup() } returns backup
            coEvery { backupManager.restoreLatestBackup(any()) } returns
                BackupRestoreOutcome(lineageId = "l1", formatVersion = 1, backupAtMillis = null, contentHash = null)

            val viewModel = BackupRestoreOfferViewModel(backupManager)
            advanceUntilIdle()
            viewModel.onRecoveryKeyChanged("key")
            viewModel.restore()
            viewModel.restore()
            advanceUntilIdle()

            coVerify(exactly = 1) { backupManager.restoreLatestBackup(any()) }
        }
}
