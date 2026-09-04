package com.sanchr.feature.settings

import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.notifications.PushTokenManager
import com.sanchr.domain.messaging.DeleteAccountUseCase
import com.sanchr.proto.notifications.NotificationServiceClient
import com.sanchr.proto.settings.SettingsServiceClient
import com.sanchr.sync.backup.ChatBackupManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Profile-photo visibility is a tri-state on the wire ("everyone" /
 * "contacts" / "nobody", per settings.proto and iOS). Android collapsed it to
 * a Boolean, so choosing "Contacts Only" silently stored "Everyone" — a
 * privacy control that claimed more protection than it applied. These tests
 * pin the three states and the local persistence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePhotoVisibilityTest {
    private val testDispatcher = StandardTestDispatcher()

    private val userPreferences =
        mockk<UserPreferences>(relaxed = true).also {
            // Every flow the uiState combine depends on must emit, or the
            // combine never produces a value and uiState stays at its initial.
            every { it.themeMode } returns flowOf("system")
            every { it.dynamicColorEnabled } returns flowOf(false)
            every { it.notificationsEnabled } returns flowOf(true)
            every { it.readReceiptsEnabled } returns flowOf(true)
            every { it.biometricEnabled } returns flowOf(false)
            every { it.screenshotProtectionEnabled } returns flowOf(false)
            every { it.disappearingDefaultSeconds } returns flowOf(0)
            every { it.profilePhotoVisibility } returns flowOf("contacts")
        }
    private val deleteAccountUseCase = mockk<DeleteAccountUseCase>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel() =
        SettingsViewModel(
            userPreferences = userPreferences,
            recoveryKeyManager = mockk(relaxed = true),
            notificationServiceClient = mockk<NotificationServiceClient>(relaxed = true),
            pushTokenManager = mockk<PushTokenManager>(relaxed = true),
            settingsServiceClient = mockk<SettingsServiceClient>(relaxed = true),
            chatBackupManager = mockk<ChatBackupManager>(relaxed = true),
            deleteAccountUseCase = deleteAccountUseCase,
        )

    @Test
    fun `choosing contacts only stores contacts, not everyone`() =
        runTest(testDispatcher) {
            val viewModel = newViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }

            viewModel.setProfilePhotoVisibility("contacts")
            advanceUntilIdle()

            // The bug this replaces: setProfilePhotoVisible(option != "Nobody")
            // mapped "Contacts Only" to true, i.e. "Everyone".
            coVerify(exactly = 1) { userPreferences.setProfilePhotoVisibility("contacts") }
            assertEquals("contacts", viewModel.uiState.value.profilePhotoVisibility)
        }

    @Test
    fun `each of the three states round-trips through the ui state`() =
        runTest(testDispatcher) {
            val viewModel = newViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            // Let init's seed-from-preferences settle first, or it lands
            // mid-loop and overwrites the first value set.
            advanceUntilIdle()

            listOf("everyone", "contacts", "nobody").forEach { visibility ->
                viewModel.setProfilePhotoVisibility(visibility)
                advanceUntilIdle()
                assertEquals(visibility, viewModel.uiState.value.profilePhotoVisibility)
            }
        }

    @Test
    fun `the stored value seeds the ui on open`() =
        runTest(testDispatcher) {
            val viewModel = newViewModel()
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // Persisted as "contacts" above; the screen must not reopen on the
            // default and misreport the user's choice back to them.
            assertEquals("contacts", viewModel.uiState.value.profilePhotoVisibility)
        }
}
