package com.sanchr.feature.settings

import com.sanchr.core.crypto.RecoveryKeyManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.notifications.PushTokenManager
import com.sanchr.domain.messaging.DeleteAccountUseCase
import com.sanchr.proto.notifications.NotificationServiceClient
import com.sanchr.proto.settings.SetRegistrationLockRequest
import com.sanchr.proto.settings.SetRegistrationLockResponse
import com.sanchr.proto.settings.SettingsServiceClient
import com.sanchr.sync.backup.ChatBackupManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelRegistrationLockTest {
    private val dispatcher = StandardTestDispatcher()
    private val settingsServiceClient = mockk<SettingsServiceClient>(relaxed = true)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() =
        SettingsViewModel(
            userPreferences = mockk<UserPreferences>(relaxed = true),
            recoveryKeyManager = mockk<RecoveryKeyManager>(relaxed = true),
            notificationServiceClient = mockk<NotificationServiceClient>(relaxed = true),
            pushTokenManager = mockk<PushTokenManager>(relaxed = true),
            settingsServiceClient = settingsServiceClient,
            chatBackupManager = mockk<ChatBackupManager>(relaxed = true),
            deleteAccountUseCase = mockk<DeleteAccountUseCase>(relaxed = true),
            profileUpdater = mockk(relaxed = true),
        )

    @Test
    fun `enabling, changing and disabling send the PINs the server needs to verify`() =
        runTest(dispatcher) {
            coEvery { settingsServiceClient.setRegistrationLock(any()) } returns SetRegistrationLockResponse(success = true)
            val vm = vm()

            assertNull(vm.setRegistrationLock(enabled = true, pin = "123456"))
            assertNull(vm.setRegistrationLock(enabled = true, pin = "654321", currentPin = "123456"))
            assertNull(vm.setRegistrationLock(enabled = false, pin = "", currentPin = "654321"))

            coVerify { settingsServiceClient.setRegistrationLock(match { it.enabled && it.pin == "123456" && it.currentPin == "" }) }
            coVerify { settingsServiceClient.setRegistrationLock(match { it.enabled && it.pin == "654321" && it.currentPin == "123456" }) }
            coVerify { settingsServiceClient.setRegistrationLock(match { !it.enabled && it.currentPin == "654321" }) }
        }

    @Test
    fun `a refused or failed request comes back as a message`() =
        runTest(dispatcher) {
            coEvery { settingsServiceClient.setRegistrationLock(any<SetRegistrationLockRequest>()) } returns
                SetRegistrationLockResponse(success = false)
            assertEquals("The PIN was not accepted", vm().setRegistrationLock(enabled = false, pin = "", currentPin = "000000"))
            coEvery { settingsServiceClient.setRegistrationLock(any<SetRegistrationLockRequest>()) } throws IOException("offline")
            assertEquals("offline", vm().setRegistrationLock(enabled = true, pin = "123456"))
        }
}
