package com.sanchr.feature.profile

import androidx.lifecycle.SavedStateHandle
import com.sanchr.core.crypto.profile.EncryptedProfileUpdater
import com.sanchr.core.network.media.AvatarUploader
import com.sanchr.proto.settings.ProfileResponse
import com.sanchr.proto.settings.SettingsServiceClient
import com.sanchr.proto.settings.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelAvatarTest {
    private val dispatcher = StandardTestDispatcher()
    private val settingsClient =
        mockk<SettingsServiceClient> {
            coEvery { getSettings(any()) } returns UserSettings(displayName = "Me", bio = "hi", avatarUrl = "https://cdn/old.jpg")
        }
    private val uploader = mockk<AvatarUploader>()
    private val profileUpdater = mockk<EncryptedProfileUpdater>()
    private val jpeg = byteArrayOf(1, 2, 3)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() =
        ProfileViewModel(
            SavedStateHandle(mapOf("userId" to "me")),
            settingsClient,
            uploader,
            profileUpdater,
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

    @Test
    fun `uploads, writes the new url into the encrypted profile, then shows it`() =
        runTest(dispatcher) {
            coEvery { uploader.upload(jpeg, "image/jpeg") } returns "https://cdn/new.jpg"
            coEvery { profileUpdater.update(any(), any(), any()) } returns ProfileResponse(success = true)
            val vm = vm()
            advanceUntilIdle()

            vm.uploadAvatar(jpeg, "image/jpeg")
            advanceUntilIdle()

            coVerify { profileUpdater.update(displayName = "Me", bio = "hi", avatarUrl = "https://cdn/new.jpg") }
            assertEquals("https://cdn/new.jpg", vm.uiState.value.avatarUrl)
            assertFalse(vm.uiState.value.isUploadingAvatar)
            assertNull(vm.uiState.value.errorMessage)
        }

    @Test
    fun `a failed upload keeps the old avatar and reports an error`() =
        runTest(dispatcher) {
            coEvery { uploader.upload(any(), any()) } throws IllegalStateException("HTTP 403")
            val vm = vm()
            advanceUntilIdle()

            vm.uploadAvatar(jpeg, "image/jpeg")
            advanceUntilIdle()

            coVerify(exactly = 0) { profileUpdater.update(any(), any(), any()) }
            assertEquals("https://cdn/old.jpg", vm.uiState.value.avatarUrl)
            assertEquals("Avatar upload failed", vm.uiState.value.errorMessage)
            assertFalse(vm.uiState.value.isUploadingAvatar)
        }

    @Test
    fun `a failed profile write does not flip the UI to a photo the server does not know about`() =
        runTest(dispatcher) {
            coEvery { uploader.upload(any(), any()) } returns "https://cdn/new.jpg"
            coEvery { profileUpdater.update(any(), any(), any()) } throws IllegalStateException("UNAVAILABLE")
            val vm = vm()
            advanceUntilIdle()

            vm.uploadAvatar(jpeg, "image/jpeg")
            advanceUntilIdle()

            assertEquals("https://cdn/old.jpg", vm.uiState.value.avatarUrl)
            assertEquals("Avatar upload failed", vm.uiState.value.errorMessage)
        }
}
