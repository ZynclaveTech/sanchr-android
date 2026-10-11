package com.sanchr.feature.settings

import android.util.Log
import app.cash.turbine.test
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.notifications.PushTokenManager
import com.sanchr.domain.messaging.DeleteAccountUseCase
import com.sanchr.domain.messaging.media.MediaCacheCleaner
import com.sanchr.proto.notifications.NotificationServiceClient
import com.sanchr.proto.settings.SettingsServiceClient
import com.sanchr.sync.backup.ChatBackupManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The notification switches, and the Clear Cache button beside them.
 *
 * Five of the switches reached the server and nowhere else, so the screen read
 * its own data-class defaults: every one of them sprang back to on the moment
 * it was tapped, and the next change pushed the stale value with it. Clear
 * Cache emitted a success event and deleted nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val mediaCacheCleaner = mockk<MediaCacheCleaner>(relaxed = true)

    private fun preferences(
        messages: Boolean = true,
        groups: Boolean = true,
        calls: Boolean = true,
        sound: Boolean = true,
        vibrate: Boolean = true,
        preview: String = "always",
        autoDownload: String = "wifi",
    ) = mockk<UserPreferences>(relaxed = true).also {
        every { it.themeMode } returns flowOf("system")
        every { it.dynamicColorEnabled } returns flowOf(false)
        every { it.notificationsEnabled } returns flowOf(true)
        every { it.readReceiptsEnabled } returns flowOf(true)
        every { it.screenLockTimeoutSeconds } returns flowOf(0)
        every { it.typingIndicatorsEnabled } returns flowOf(true)
        every { it.onlineStatusVisible } returns flowOf(true)
        every { it.canSendReadReceipts } returns flowOf(true)
        every { it.biometricEnabled } returns flowOf(false)
        every { it.screenshotProtectionEnabled } returns flowOf(false)
        every { it.screenSecurityActive } returns flowOf(false)
        every { it.disappearingDefaultSeconds } returns flowOf(0)
        every { it.profilePhotoVisibility } returns flowOf("everyone")
        every { it.notificationPreview } returns flowOf(preview)
        every { it.messageNotificationsEnabled } returns flowOf(messages)
        every { it.groupNotificationsEnabled } returns flowOf(groups)
        every { it.callNotificationsEnabled } returns flowOf(calls)
        every { it.notificationSoundEnabled } returns flowOf(sound)
        every { it.notificationVibrateEnabled } returns flowOf(vibrate)
        every { it.mediaAutoDownload } returns flowOf(autoDownload)
    }

    private fun viewModel(preferences: UserPreferences) =
        SettingsViewModel(
            userPreferences = preferences,
            recoveryKeyManager = mockk(relaxed = true),
            notificationServiceClient = mockk<NotificationServiceClient>(relaxed = true),
            pushTokenManager = mockk<PushTokenManager>(relaxed = true),
            settingsServiceClient = mockk<SettingsServiceClient>(relaxed = true),
            chatBackupManager = mockk<ChatBackupManager>(relaxed = true),
            deleteAccountUseCase = mockk<DeleteAccountUseCase>(relaxed = true),
            profileUpdater = mockk(relaxed = true),
            sessionManager = mockk(relaxed = true),
            mediaCacheCleaner = mediaCacheCleaner,
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // The failure path logs, and android.util.Log throws in a JVM test.
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    /**
     * What the phone enforces is what the switch shows. These two were shown
     * from the server's copy, which defaults to on and can be out of date, so
     * a switch could read on while the phone sent nothing — or off while it
     * still sent.
     */
    @Test
    fun `typing and online status are shown from the values the phone enforces`() =
        runTest(dispatcher) {
            val prefs =
                preferences().also {
                    every { it.typingIndicatorsEnabled } returns flowOf(false)
                    every { it.onlineStatusVisible } returns flowOf(false)
                }

            viewModel(prefs).uiState.test {
                advanceUntilIdle()
                val state = expectMostRecentItem()
                assertFalse(state.typingIndicatorsEnabled)
                assertFalse(state.onlineStatusVisible)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** Saved and enforced all along, and never shown: the picker always said "Immediately". */
    @Test
    fun `the lock timeout that was chosen is the one shown`() =
        runTest(dispatcher) {
            val prefs =
                preferences().also {
                    every { it.screenLockEnabled } returns flowOf(true)
                    every { it.screenLockTimeoutSeconds } returns flowOf(300)
                }

            viewModel(prefs).uiState.test {
                advanceUntilIdle()
                val state = expectMostRecentItem()
                assertEquals("5m", state.screenLockTimeout)
                assertTrue(state.screenLockEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `choosing a timeout stores its seconds`() =
        runTest(dispatcher) {
            val prefs = preferences()

            viewModel(prefs).setScreenLockTimeout("30m")
            advanceUntilIdle()

            coVerify { prefs.setScreenLockTimeoutSeconds(1_800) }
        }

    @Test
    fun `a stored timeout the picker does not offer reads as immediately, not as nothing`() =
        runTest(dispatcher) {
            val prefs = preferences().also { every { it.screenLockTimeoutSeconds } returns flowOf(77) }

            viewModel(prefs).uiState.test {
                advanceUntilIdle()
                assertEquals("immediately", expectMostRecentItem().screenLockTimeout)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `switches that were turned off come back off`() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    preferences(
                        messages = false,
                        groups = false,
                        calls = false,
                        sound = false,
                        vibrate = false,
                        preview = "never",
                    ),
                )

            // Collected, not read: the state flow only runs its combine while
            // something is subscribed.
            viewModel.uiState.test {
                advanceUntilIdle()
                val state = expectMostRecentItem()
                assertFalse(state.messageNotificationsEnabled)
                assertFalse(state.groupNotificationsEnabled)
                assertFalse(state.callNotificationsEnabled)
                assertFalse(state.notificationSoundEnabled)
                assertFalse(state.notificationVibrationEnabled)
                assertEquals("never", state.notificationPreview)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `turning a switch off persists it, not only tells the server`() =
        runTest(dispatcher) {
            val preferences = preferences()
            val viewModel = viewModel(preferences)
            advanceUntilIdle()

            viewModel.setMessageNotificationsEnabled(false)
            viewModel.setGroupNotificationsEnabled(false)
            viewModel.setCallNotificationsEnabled(false)
            viewModel.setNotificationSoundEnabled(false)
            viewModel.setNotificationVibrationEnabled(false)
            advanceUntilIdle()

            coVerify { preferences.setMessageNotificationsEnabled(false) }
            coVerify { preferences.setGroupNotificationsEnabled(false) }
            coVerify { preferences.setCallNotificationsEnabled(false) }
            coVerify { preferences.setNotificationSoundEnabled(false) }
            coVerify { preferences.setNotificationVibrateEnabled(false) }
        }

    @Test
    fun `the auto-download choice is read back from storage, so Never can be shown`() =
        runTest(dispatcher) {
            val viewModel = viewModel(preferences(autoDownload = "never"))

            viewModel.uiState.test {
                advanceUntilIdle()
                // Read from the lossy wire boolean this was always "wifi".
                assertEquals("never", expectMostRecentItem().mediaAutoDownload)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `clearing the cache deletes it and reports what was freed`() =
        runTest(dispatcher) {
            coEvery { mediaCacheCleaner.clear() } returns 2_048L
            coEvery { mediaCacheCleaner.cachedBytes() } returns 0L
            val viewModel = viewModel(preferences())
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.clearCache()
                advanceUntilIdle()

                assertEquals(SettingsEvent.CacheCleared(freedBytes = 2_048L), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { mediaCacheCleaner.clear() }
        }

    @Test
    fun `a cache that cannot be cleared says so instead of claiming success`() =
        runTest(dispatcher) {
            coEvery { mediaCacheCleaner.clear() } throws IllegalStateException("read-only")
            val viewModel = viewModel(preferences())
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.clearCache()
                advanceUntilIdle()

                assertEquals(SettingsEvent.Error("Couldn't clear the cache"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
