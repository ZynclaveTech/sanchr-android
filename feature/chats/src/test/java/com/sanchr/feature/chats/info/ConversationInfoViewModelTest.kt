package com.sanchr.feature.chats.info

import androidx.lifecycle.SavedStateHandle
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.ConversationType
import com.sanchr.domain.messaging.MessageRepository
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

    private fun viewModel() = ConversationInfoViewModel(messageRepository, SavedStateHandle(mapOf("conversationId" to "c1")))

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
}
