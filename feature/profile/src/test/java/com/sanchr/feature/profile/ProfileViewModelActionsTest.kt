package com.sanchr.feature.profile

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.sanchr.core.crypto.profile.EncryptedProfileUpdater
import com.sanchr.core.network.media.AvatarUploader
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.proto.settings.SettingsServiceClient
import com.sanchr.proto.settings.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Message on a profile resolves the chat first: the route wants a conversation id, not a user id. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelActionsTest {
    private val dispatcher = StandardTestDispatcher()
    private val settingsClient = mockk<SettingsServiceClient>()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { settingsClient.getSettings(any()) } returns UserSettings(displayName = "Ada", phoneNumber = "+15550100")
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(userId: String) =
        ProfileViewModel(
            SavedStateHandle(mapOf("userId" to userId)),
            settingsClient,
            mockk<AvatarUploader>(relaxed = true),
            mockk<EncryptedProfileUpdater>(relaxed = true),
            mockk<ContactRepository>(relaxed = true),
            messageRepository,
        )

    @Test
    fun `opening the chat resolves the conversation for this peer, and a double tap resolves it once`() =
        runTest(dispatcher) {
            coEvery { messageRepository.ensureConversation("peer-1") } returns "conv-9"
            val vm = vm("peer-1")
            advanceUntilIdle()

            vm.events.test {
                vm.openConversation()
                vm.openConversation()
                advanceUntilIdle()

                val event = awaitItem()
                assertIs<ProfileEvent.OpenConversation>(event)
                assertEquals("conv-9", event.conversationId)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 1) { messageRepository.ensureConversation("peer-1") }
        }

    @Test
    fun `a failure to resolve the chat is reported rather than silent`() =
        runTest(dispatcher) {
            coEvery { messageRepository.ensureConversation("peer-1") } throws IOException("offline")
            val vm = vm("peer-1")
            advanceUntilIdle()

            vm.events.test {
                vm.openConversation()
                advanceUntilIdle()

                assertEquals(ProfileEvent.Error("offline"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `our own profile never opens a chat`() =
        runTest(dispatcher) {
            val vm = vm("me")
            advanceUntilIdle()

            vm.openConversation()
            advanceUntilIdle()

            coVerify(exactly = 0) { messageRepository.ensureConversation(any()) }
        }
}
