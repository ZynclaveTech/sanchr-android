package com.sanchr.feature.chats

import androidx.lifecycle.SavedStateHandle
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.PresenceStore
import com.sanchr.sync.realtime.RealtimeManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant

/**
 * The transcript is a window, not the whole conversation.
 *
 * The observer was unbounded, so opening a long chat streamed every message it
 * had ever held into memory and into composition on each change — and the
 * `loadMoreMessages` beside it called a repository method that ignores its own
 * `beforeTimestamp` and re-queries the newest page, merged into nothing.
 * Nothing called it, which is the only reason that never showed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatDetailViewModelWindowTest {
    private val dispatcher = StandardTestDispatcher()
    private val conversationId = "conv-1"
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val realtimeManager = mockk<RealtimeManager>(relaxed = true)

    private fun message(index: Int) =
        Message(
            id = "m$index",
            conversationId = conversationId,
            senderId = "peer",
            content = MessageContent.Text(body = "body $index"),
            status = com.sanchr.core.model.MessageStatus.DELIVERED,
            timestamp = Instant.fromEpochMilliseconds(1_000L + index),
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { messageRepository.observeConversation(any()) } returns emptyFlow()
        every { realtimeManager.typingCache } returns MutableStateFlow(emptyMap())
        coEvery { messageRepository.oneToOneRecipient(any(), any()) } returns "peer"
        coEvery { messageRepository.draftText(any()) } returns null
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel() =
        ChatDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("conversationId" to conversationId)),
            messageRepository = messageRepository,
            sendMessageUseCase = mockk(relaxed = true),
            sendAttachmentUseCase = mockk(relaxed = true),
            sendAlbumUseCase = mockk(relaxed = true),
            attachmentDownloader = mockk(relaxed = true),
            sendReadReceiptUseCase = mockk(relaxed = true),
            toggleReactionUseCase = mockk(relaxed = true),
            consumeViewOnceUseCase = mockk(relaxed = true),
            forwardMessageUseCase = mockk(relaxed = true),
            presenceStore = PresenceStore { 0L },
            sessionManager = mockk<SessionManager> { every { getUserId() } returns "self" },
            realtimeManager = realtimeManager,
            notificationHandler = mockk(relaxed = true),
            userPreferences =
                mockk<UserPreferences>(relaxed = true) {
                    every { linkPreviewsEnabled } returns flowOf(true)
                    every { screenshotProtectionEnabled } returns flowOf(true)
                    every { chatWallpaper } returns flowOf("default")
                    every { mediaAutoDownload } returns flowOf("wifi")
                    every { typingIndicatorsEnabled } returns flowOf(false)
                },
            linkPreviewFetcher = mockk(relaxed = true),
            safetyNumbers = mockk(relaxed = true),
            connectivityMonitor = mockk(relaxed = true),
            appContext = mockk(relaxed = true),
        )

    @Test
    fun `the chat opens on a bounded window, not the whole conversation`() =
        runTest(dispatcher) {
            every { messageRepository.observeRecentMessages(conversationId, any()) } returns flowOf(emptyList())

            newViewModel()
            advanceUntilIdle()

            verify { messageRepository.observeRecentMessages(conversationId, ChatDetailViewModel.INITIAL_WINDOW) }
        }

    @Test
    fun `a full window means there may be more behind it`() =
        runTest(dispatcher) {
            val full = List(ChatDetailViewModel.INITIAL_WINDOW) { message(it) }
            every { messageRepository.observeRecentMessages(conversationId, any()) } returns flowOf(full)

            val viewModel = newViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hasMoreMessages)
        }

    @Test
    fun `a short window means the start of the conversation is on screen`() =
        runTest(dispatcher) {
            every { messageRepository.observeRecentMessages(conversationId, any()) } returns flowOf(listOf(message(1)))

            val viewModel = newViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.hasMoreMessages)
            assertEquals(1, viewModel.uiState.value.messages.size)
        }

    @Test
    fun `scrolling back asks for a larger window`() =
        runTest(dispatcher) {
            val full = List(ChatDetailViewModel.INITIAL_WINDOW) { message(it) }
            every { messageRepository.observeRecentMessages(conversationId, any()) } returns flowOf(full)
            val viewModel = newViewModel()
            advanceUntilIdle()

            viewModel.loadMoreMessages()
            advanceUntilIdle()

            verify {
                messageRepository.observeRecentMessages(
                    conversationId,
                    ChatDetailViewModel.INITIAL_WINDOW + ChatDetailViewModel.WINDOW_STEP,
                )
            }
        }

    @Test
    fun `there is nothing to load once the start is reached`() =
        runTest(dispatcher) {
            every { messageRepository.observeRecentMessages(conversationId, any()) } returns flowOf(listOf(message(1)))
            val viewModel = newViewModel()
            advanceUntilIdle()

            viewModel.loadMoreMessages()
            advanceUntilIdle()

            // No wider window asked for, and no spinner left spinning.
            verify(exactly = 0) {
                messageRepository.observeRecentMessages(
                    conversationId,
                    ChatDetailViewModel.INITIAL_WINDOW + ChatDetailViewModel.WINDOW_STEP,
                )
            }
            assertFalse(viewModel.uiState.value.isLoadingMore)
        }
}
