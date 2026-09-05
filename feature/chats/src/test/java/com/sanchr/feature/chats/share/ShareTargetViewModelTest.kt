package com.sanchr.feature.chats.share

import com.sanchr.core.common.Result
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.ConversationType
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.SendAttachmentUseCase
import com.sanchr.domain.messaging.SendMessageUseCase
import com.sanchr.domain.messaging.media.AttachmentUploader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ShareTargetViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val sendMessageUseCase = mockk<SendMessageUseCase>(relaxed = true)
    private val sendAttachmentUseCase = mockk<SendAttachmentUseCase>(relaxed = true)

    private fun conversation(
        id: String,
        title: String,
    ) = Conversation(
        id = id,
        type = ConversationType.DIRECT,
        participants = emptyList(),
        title = title,
        updatedAt = Instant.fromEpochMilliseconds(1),
        createdAt = Instant.fromEpochMilliseconds(0),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { messageRepository.observeConversations() } returns
            flowOf(listOf(conversation("c1", "Ravi Kumar"), conversation("c2", "Aditi Sharma")))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ShareTargetViewModel(messageRepository, sendMessageUseCase, sendAttachmentUseCase)

    @Test
    fun `search narrows the list without losing the selection`() {
        val model = viewModel()
        model.toggleSelected("c1")

        model.onQueryChanged("aditi")

        assertEquals(
            listOf("c2"),
            model.uiState.value.visibleConversations
                .map { it.id },
        )
        assertEquals(setOf("c1"), model.uiState.value.selectedIds, "a filter must not silently drop what was chosen")
    }

    @Test
    fun `nothing selected means nothing to proceed with`() {
        val model = viewModel()

        assertFalse(model.uiState.value.canProceed)
        model.proceedToCompose()
        assertEquals(ShareStep.Picking, model.uiState.value.step)

        model.toggleSelected("c1")
        assertTrue(model.uiState.value.canProceed)
        model.proceedToCompose()
        assertEquals(ShareStep.Composing, model.uiState.value.step)
    }

    @Test
    fun `text goes to every chosen chat`() =
        runTest(dispatcher) {
            coEvery { sendMessageUseCase(any(), any()) } returns Result.Success(mockk(relaxed = true))
            val model = viewModel()
            model.toggleSelected("c1")
            model.toggleSelected("c2")

            model.sendText("hello")

            coVerify(exactly = 1) { sendMessageUseCase("c1", "hello") }
            coVerify(exactly = 1) { sendMessageUseCase("c2", "hello") }
            assertEquals(ShareOutcome(sent = 2, failed = 0), model.uiState.value.outcome)
        }

    @Test
    fun `one failed chat is reported without claiming the rest failed`() =
        runTest(dispatcher) {
            coEvery { sendMessageUseCase("c1", any()) } returns Result.Success(mockk(relaxed = true))
            coEvery { sendMessageUseCase("c2", any()) } returns Result.Error(IllegalStateException("offline"))
            val model = viewModel()
            model.toggleSelected("c1")
            model.toggleSelected("c2")

            model.sendText("hello")

            assertEquals(ShareOutcome(sent = 1, failed = 1), model.uiState.value.outcome)
        }

    @Test
    fun `a use case that throws counts as a failure rather than crashing the share`() =
        runTest(dispatcher) {
            coEvery { sendMessageUseCase(any(), any()) } throws IllegalStateException("db closed")
            val model = viewModel()
            model.toggleSelected("c1")

            model.sendText("hello")

            assertEquals(ShareOutcome(sent = 0, failed = 1), model.uiState.value.outcome)
        }

    @Test
    fun `the caption rides the first file only, so a set of photos does not repeat it`() =
        runTest(dispatcher) {
            val sent = mutableListOf<AttachmentUploader.Prepared>()
            coEvery { sendAttachmentUseCase("c1", capture(sent)) } returns Result.Success(mockk(relaxed = true))
            val model = viewModel()
            model.toggleSelected("c1")
            model.onCaptionChanged("  on the beach  ")

            model.sendAttachments(
                listOf(
                    AttachmentUploader.Prepared(byteArrayOf(1), "image/jpeg", "a.jpg"),
                    AttachmentUploader.Prepared(byteArrayOf(2), "image/jpeg", "b.jpg"),
                ),
            )

            assertEquals(listOf("on the beach", null), sent.map { it.caption })
            assertEquals(ShareOutcome(sent = 1, failed = 0), model.uiState.value.outcome)
        }

    @Test
    fun `nothing readable to attach is reported as a failure, not a silent success`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.toggleSelected("c1")

            model.sendAttachments(emptyList())

            assertEquals(ShareOutcome(sent = 0, failed = 1), model.uiState.value.outcome)
            coVerify(exactly = 0) { sendAttachmentUseCase(any(), any()) }
        }

    @Test
    fun `the sharing app's text seeds the caption but never overwrites what was typed`() {
        val model = viewModel()
        model.seedCaption("from the gallery")
        assertEquals("from the gallery", model.uiState.value.caption)

        model.onCaptionChanged("mine")
        model.seedCaption("from the gallery")
        assertEquals("mine", model.uiState.value.caption)
    }
}
