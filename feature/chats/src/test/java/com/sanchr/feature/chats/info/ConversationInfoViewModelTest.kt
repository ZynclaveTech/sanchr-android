package com.sanchr.feature.chats.info

import androidx.lifecycle.SavedStateHandle
import com.sanchr.core.model.ChatVaultPolicy
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.ConversationType
import com.sanchr.core.model.User
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.domain.messaging.ClearChatUseCase
import com.sanchr.domain.messaging.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationInfoViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val contactRepository = mockk<ContactRepository>(relaxed = true)
    private val clearChat = mockk<ClearChatUseCase>(relaxed = true)

    private fun conversation(
        durationMs: Long? = null,
        wallpaper: String? = null,
        muted: Boolean = false,
        archived: Boolean = false,
    ) = Conversation(
        id = "c1",
        type = ConversationType.DIRECT,
        participants = emptyList(),
        title = "Ravi Kumar",
        isMuted = muted,
        isArchived = archived,
        disappearingMessageDuration = durationMs,
        wallpaper = wallpaper,
        updatedAt = Instant.fromEpochMilliseconds(1),
        createdAt = Instant.fromEpochMilliseconds(0),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { messageRepository.observeConversation("c1") } returns flowOf(conversation())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        ConversationInfoViewModel(
            messageRepository,
            mockk { every { mediaVisibleInGallery("c1") } returns flowOf(true) },
            SavedStateHandle(mapOf("conversationId" to "c1")),
            contactRepository,
            clearChat,
        )

    private fun person(
        id: String,
        name: String,
    ) = User(id = id, phoneNumber = "", displayName = name, createdAt = Instant.fromEpochMilliseconds(0))

    private fun chatWith(
        type: ConversationType,
        vararg people: User,
    ) = conversation().copy(type = type, participants = listOf(person("self", "You")) + people)

    @Test
    fun `clearing the chat clears this chat`() =
        runTest(dispatcher) {
            coEvery { clearChat("c1") } returns 3

            viewModel().clearMessages()

            coVerify(exactly = 1) { clearChat("c1") }
        }

    @Test
    fun `a clear that fails says so`() =
        runTest(dispatcher) {
            coEvery { clearChat("c1") } throws IllegalStateException("db")
            val viewModel = viewModel()

            viewModel.clearMessages()

            assertNotNull(viewModel.uiState.value.error)
        }

    /** Blocking lived on the profile and the contact list, and not in the chat. */
    @Test
    fun `the other person in a one-to-one chat can be blocked and unblocked from here`() =
        runTest(dispatcher) {
            every { messageRepository.observeConversation("c1") } returns
                flowOf(chatWith(ConversationType.DIRECT, person("ravi", "Ravi Kumar")))
            coEvery { contactRepository.isBlocked("ravi") } returns false
            val viewModel = viewModel()
            assertEquals("ravi", viewModel.uiState.value.peerId)
            assertFalse(viewModel.uiState.value.isBlocked)

            viewModel.setBlocked(true)
            assertTrue(viewModel.uiState.value.isBlocked)
            coVerify { contactRepository.setBlocked("ravi", true) }

            viewModel.setBlocked(false)
            assertFalse(viewModel.uiState.value.isBlocked)
            coVerify { contactRepository.setBlocked("ravi", false) }
        }

    @Test
    fun `someone already blocked is shown as blocked`() =
        runTest(dispatcher) {
            every { messageRepository.observeConversation("c1") } returns
                flowOf(chatWith(ConversationType.DIRECT, person("ravi", "Ravi Kumar")))
            coEvery { contactRepository.isBlocked("ravi") } returns true

            assertTrue(viewModel().uiState.value.isBlocked)
        }

    @Test
    fun `a block that fails says so and does not pretend`() =
        runTest(dispatcher) {
            every { messageRepository.observeConversation("c1") } returns
                flowOf(chatWith(ConversationType.DIRECT, person("ravi", "Ravi Kumar")))
            coEvery { contactRepository.isBlocked("ravi") } returns false
            coEvery { contactRepository.setBlocked("ravi", true) } throws IllegalStateException("offline")
            val viewModel = viewModel()

            viewModel.setBlocked(true)

            assertFalse(viewModel.uiState.value.isBlocked)
            assertNotNull(viewModel.uiState.value.error)
        }

    /** A group has no one person; the page used to pick whoever came first. */
    @Test
    fun `a group has nobody to block or compare codes with`() =
        runTest(dispatcher) {
            every { messageRepository.observeConversation("c1") } returns
                flowOf(chatWith(ConversationType.GROUP, person("ravi", "Ravi Kumar"), person("meera", "Meera")))
            val viewModel = viewModel()

            assertNull(viewModel.uiState.value.peerId)
            viewModel.setBlocked(true)

            coVerify(exactly = 0) { contactRepository.setBlocked(any(), any()) }
            coVerify(exactly = 0) { contactRepository.isBlocked(any()) }
        }

    @Test
    fun `the stored timer decides which option is shown`() {
        every { messageRepository.observeConversation("c1") } returns flowOf(conversation(durationMs = 300_000L))

        assertEquals("5m", viewModel().uiState.value.disappearingLabel)
    }

    @Test
    fun `no stored timer reads as off, so the account default applies`() {
        assertEquals("off", viewModel().uiState.value.disappearingLabel)
    }

    @Test
    fun `choosing off clears the timer rather than storing zero`() =
        runTest(dispatcher) {
            viewModel().setDisappearing("off")

            coVerify(exactly = 1) { messageRepository.setDisappearingDuration("c1", null) }
        }

    @Test
    fun `choosing a duration stores its milliseconds`() =
        runTest(dispatcher) {
            viewModel().setDisappearing("24h")

            coVerify(exactly = 1) { messageRepository.setDisappearingDuration("c1", 86_400_000L) }
        }

    @Test
    fun `choosing the default wallpaper clears the override so the account choice applies`() =
        runTest(dispatcher) {
            viewModel().setWallpaper("default")

            coVerify(exactly = 1) { messageRepository.setWallpaper("c1", null) }
        }

    @Test
    fun `choosing a wallpaper stores it for this chat only`() =
        runTest(dispatcher) {
            viewModel().setWallpaper("indigo")

            coVerify(exactly = 1) { messageRepository.setWallpaper("c1", "indigo") }
        }

    @Test
    fun `mute archive and hide reach the store`() =
        runTest(dispatcher) {
            val model = viewModel()

            model.setMuted(true)
            model.setArchived(true)
            model.hide()

            coVerify(exactly = 1) { messageRepository.setMuted("c1", true) }
            coVerify(exactly = 1) { messageRepository.setArchived("c1", true) }
            coVerify(exactly = 1) { messageRepository.setHidden("c1", true) }
        }

    @Test
    fun `a store failure is reported rather than crashing the page`() =
        runTest(dispatcher) {
            coEvery { messageRepository.setMuted(any(), any()) } throws IllegalStateException("db closed")
            val model = viewModel()

            model.setMuted(true)

            assertEquals("db closed", model.uiState.value.error)
        }

    @Test
    fun `flipping one vault switch stores the whole policy with only that one changed`() =
        runTest(dispatcher) {
            val stored = ChatVaultPolicy(captureProtection = true)
            every { messageRepository.observeConversation("c1") } returns flowOf(conversation().copy(vaultPolicy = stored))
            val viewModel = viewModel()

            viewModel.updateVaultPolicy { it.copy(viewOnceOutgoing = true) }

            val expected = ChatVaultPolicy(viewOnceOutgoing = true, captureProtection = true)
            coVerify(exactly = 1) { messageRepository.setVaultPolicy("c1", expected) }
            assertEquals(
                expected,
                viewModel.uiState.value.conversation
                    ?.vaultPolicy,
            )
        }

    @Test
    fun `two switches flipped in a row both land`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.updateVaultPolicy { it.copy(viewOnceOutgoing = true) }
            viewModel.updateVaultPolicy { it.copy(captureProtection = true) }

            // The second change is applied to the first's result, not to the
            // policy the screen had drawn before either.
            coVerify { messageRepository.setVaultPolicy("c1", ChatVaultPolicy(viewOnceOutgoing = true, captureProtection = true)) }
        }

    @Test
    fun `a change that changes nothing writes nothing`() =
        runTest(dispatcher) {
            viewModel().updateVaultPolicy { it.copy(viewOnceOutgoing = false) }

            coVerify(exactly = 0) { messageRepository.setVaultPolicy(any(), any()) }
        }

    @Test
    fun `nothing is written before the conversation has loaded`() =
        runTest(dispatcher) {
            every { messageRepository.observeConversation("c1") } returns emptyFlow()

            // A write now would be built from defaults and would overwrite
            // whatever policy the chat really has.
            viewModel().updateVaultPolicy { it.copy(viewOnceOutgoing = true) }

            coVerify(exactly = 0) { messageRepository.setVaultPolicy(any(), any()) }
        }
}
