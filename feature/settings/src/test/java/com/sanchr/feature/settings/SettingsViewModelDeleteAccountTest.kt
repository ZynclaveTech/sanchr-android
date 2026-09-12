package com.sanchr.feature.settings

import com.sanchr.core.common.Result
import com.sanchr.core.crypto.RecoveryKeyManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.notifications.PushTokenManager
import com.sanchr.domain.messaging.DeleteAccountUseCase
import com.sanchr.proto.notifications.NotificationServiceClient
import com.sanchr.proto.settings.SettingsServiceClient
import com.sanchr.sync.backup.ChatBackupManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Account deletion is the only irreversible action in the app and the only
 * way out of Sanchr, so its ViewModel behaviour is pinned here: the in-flight
 * guard, the failure surfacing, and the fact that a failed delete leaves the
 * user on the screen able to retry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelDeleteAccountTest {
    private val testDispatcher = StandardTestDispatcher()

    private val userPreferences =
        mockk<UserPreferences>(relaxed = true).also {
            every { it.disappearingDefaultSeconds } returns flowOf(0)
            every { it.profilePhotoVisibility } returns flowOf("everyone")
        }
    private val recoveryKeyManager = mockk<RecoveryKeyManager>(relaxed = true)
    private val notificationServiceClient = mockk<NotificationServiceClient>(relaxed = true)
    private val pushTokenManager = mockk<PushTokenManager>(relaxed = true)
    private val settingsServiceClient = mockk<SettingsServiceClient>(relaxed = true)
    private val chatBackupManager = mockk<ChatBackupManager>(relaxed = true)
    private val deleteAccountUseCase = mockk<DeleteAccountUseCase>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel() =
        SettingsViewModel(
            userPreferences = userPreferences,
            recoveryKeyManager = recoveryKeyManager,
            notificationServiceClient = notificationServiceClient,
            pushTokenManager = pushTokenManager,
            settingsServiceClient = settingsServiceClient,
            chatBackupManager = chatBackupManager,
            deleteAccountUseCase = deleteAccountUseCase,
            profileUpdater = mockk(relaxed = true),
            // The display name falls back to the session when the server sends
            // none; these tests do not exercise the profile card.
            sessionManager = mockk(relaxed = true),
        )

    @Test
    fun `a successful delete clears the push token and runs the use case once`() =
        runTest(testDispatcher) {
            coEvery { deleteAccountUseCase() } returns Result.Success(Unit)

            val viewModel = newViewModel()
            viewModel.deleteAccount()
            advanceUntilIdle()

            coVerify(exactly = 1) { pushTokenManager.clearToken() }
            coVerify(exactly = 1) { deleteAccountUseCase() }
        }

    @Test
    fun `a failed delete surfaces the reason so the user can retry`() =
        runTest(testDispatcher) {
            coEvery { deleteAccountUseCase() } returns Result.Error(IllegalStateException("UNAVAILABLE"))

            val viewModel = newViewModel()
            viewModel.deleteAccount()
            advanceUntilIdle()

            val state = viewModel.accountDeletion.value
            assertIs<AccountDeletionState.Failed>(state)
            assertEquals("UNAVAILABLE", state.message)
        }

    @Test
    fun `dismissing the error returns to idle`() =
        runTest(testDispatcher) {
            coEvery { deleteAccountUseCase() } returns Result.Error(IllegalStateException("boom"))

            val viewModel = newViewModel()
            viewModel.deleteAccount()
            advanceUntilIdle()
            viewModel.dismissAccountDeletionError()

            assertEquals(AccountDeletionState.Idle, viewModel.accountDeletion.value)
        }

    @Test
    fun `a second tap while deleting does not start a second delete`() =
        runTest(testDispatcher) {
            coEvery { deleteAccountUseCase() } returns Result.Success(Unit)

            val viewModel = newViewModel()
            viewModel.deleteAccount()
            viewModel.deleteAccount()
            advanceUntilIdle()

            coVerify(exactly = 1) { deleteAccountUseCase() }
        }
}
