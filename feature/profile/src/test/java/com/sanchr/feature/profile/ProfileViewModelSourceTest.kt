package com.sanchr.feature.profile

import androidx.lifecycle.SavedStateHandle
import com.sanchr.core.crypto.profile.EncryptedProfileUpdater
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.network.media.AvatarUploader
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.domain.messaging.ContactProfileResolver
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.ResolvedProfile
import com.sanchr.proto.settings.ProfileResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Where a profile comes from.
 *
 * Both halves of this screen used to call `GetSettings`, which authenticates
 * the caller and returns *their* `user_settings` row — a row with no profile
 * columns. So a contact's profile fetched our own, and both rendered blank.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelSourceTest {
    private val dispatcher = StandardTestDispatcher()
    private val resolver = mockk<ContactProfileResolver>()
    private val profileUpdater = mockk<EncryptedProfileUpdater>(relaxed = true)
    private val sessionManager =
        mockk<SessionManager>(relaxed = true) {
            every { getUserId() } returns "user-1"
            every { getDisplayName() } returns "Ada Lovelace"
            every { getProfileBio() } returns "counting things"
            every { getProfileAvatarUrl() } returns "https://cdn/me.jpg"
            every { getStoredPhoneE164() } returns "+15550100"
        }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(userId: String) =
        ProfileViewModel(
            SavedStateHandle(mapOf("userId" to userId)),
            mockk<AvatarUploader>(relaxed = true),
            profileUpdater,
            mockk<ContactRepository>(relaxed = true),
            resolver,
            mockk<MessageRepository>(relaxed = true),
            sessionManager,
        )

    @Test
    fun `our own profile is read from the device, not from the settings call`() =
        runTest(dispatcher) {
            val viewModel = vm("me")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("user-1", state.userId)
            assertEquals("Ada Lovelace", state.displayName)
            assertEquals("counting things", state.bio)
            assertEquals("https://cdn/me.jpg", state.avatarUrl)
            assertEquals("+15550100", state.phoneNumber)
        }

    @Test
    fun `a peer's profile comes from the decrypted contact profile`() =
        runTest(dispatcher) {
            coEvery { resolver.resolvedProfile("peer-9", any()) } returns
                ResolvedProfile(
                    userId = "peer-9",
                    displayName = "Grace",
                    bio = "compiling",
                    avatarUrl = "https://cdn/grace.jpg",
                    phoneNumber = "+15550111",
                )

            val viewModel = vm("peer-9")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("peer-9", state.userId)
            assertEquals("Grace", state.displayName)
            assertEquals("compiling", state.bio)
            assertEquals("https://cdn/grace.jpg", state.avatarUrl)
        }

    @Test
    fun `saving our profile writes the plaintext to the device too`() =
        runTest(dispatcher) {
            coEvery { profileUpdater.update(any(), any(), any()) } returns ProfileResponse(success = true)
            val viewModel = vm("me")
            advanceUntilIdle()

            viewModel.onEditDisplayNameChanged("Ada L")
            viewModel.onEditBioChanged("still counting")
            viewModel.saveProfile()
            advanceUntilIdle()

            // Without this the device forgets the edit on the next launch: the
            // server never returns these fields.
            coVerify {
                sessionManager.saveProfileDetails(
                    displayName = "Ada L",
                    bio = "still counting",
                    avatarUrl = "https://cdn/me.jpg",
                )
            }
        }
}
