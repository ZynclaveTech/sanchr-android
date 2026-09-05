package com.sanchr.feature.chats

import androidx.lifecycle.SavedStateHandle
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.core.model.MessageStatus
import com.sanchr.core.notifications.NotificationHandler
import com.sanchr.domain.messaging.ForwardMessageUseCase
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.PresenceStore
import com.sanchr.domain.messaging.SendMessageUseCase
import com.sanchr.domain.messaging.SendReadReceiptUseCase
import com.sanchr.sync.realtime.RealtimeManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant

/**
 * Unit tests for the Task 4 wiring: opening a conversation
 * ([ChatDetailViewModel]'s `init` -> `markAsRead()`) must, in addition to
 * zeroing the unread count, send a read receipt naming the newest inbound
 * message via [SendReadReceiptUseCase]. Sending is resolved from
 * [MessageRepository.observeMessages] directly (not the ViewModel's
 * already-mapped [ChatDetailUiState.messages]) — see
 * [ChatDetailViewModel.sendReadReceiptForNewestInboundMessage]'s KDoc for
 * why.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatDetailViewModelMarkAsReadTest {
    private val testDispatcher = StandardTestDispatcher()

    private val messageRepository = mockk<MessageRepository>(relaxed = true)

    private val forwardMessageUseCase = mockk<ForwardMessageUseCase>(relaxed = true)
    private val sendMessageUseCase = mockk<SendMessageUseCase>()
    private val sendReadReceiptUseCase = mockk<SendReadReceiptUseCase>()
    private val sessionManager = mockk<SessionManager>()
    private val realtimeManager = mockk<RealtimeManager>(relaxed = true)
    private val notificationHandler = mockk<NotificationHandler>(relaxed = true)

    private val currentUserId = "user-self"
    private val conversationId = "conv-1"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sessionManager.getUserId() } returns currentUserId
        every { messageRepository.observeConversation(any()) } returns emptyFlow()
        every { realtimeManager.typingCache } returns MutableStateFlow(emptyMap())
        coEvery { messageRepository.markAsRead(any()) } returns Unit
        coEvery { sendReadReceiptUseCase(any(), any()) } returns Unit
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): ChatDetailViewModel =
        ChatDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("conversationId" to conversationId)),
            messageRepository = messageRepository,
            sendMessageUseCase = sendMessageUseCase,
            sendAttachmentUseCase = mockk(relaxed = true),
            attachmentDownloader = mockk(relaxed = true),
            sendReadReceiptUseCase = sendReadReceiptUseCase,
            toggleReactionUseCase = mockk(relaxed = true),
            consumeViewOnceUseCase = mockk(relaxed = true),
            forwardMessageUseCase = forwardMessageUseCase,
            presenceStore = PresenceStore(),
            sessionManager = sessionManager,
            realtimeManager = realtimeManager,
            notificationHandler = notificationHandler,
            userPreferences =
                mockk {
                    every { linkPreviewsEnabled } returns flowOf(true)
                    every { screenshotProtectionEnabled } returns flowOf(true)
                    every { mediaAutoDownload } returns flowOf("wifi")
                    every { typingIndicatorsEnabled } returns flowOf(true)
                },
            linkPreviewFetcher = mockk(relaxed = true),
            safetyNumbers = mockk(relaxed = true),
            connectivityMonitor = mockk(relaxed = true),
        )

    private fun message(
        id: String,
        senderId: String,
        timestampMillis: Long,
    ): Message =
        Message(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            content = MessageContent.Text(body = "hi"),
            status = MessageStatus.DELIVERED,
            timestamp = Instant.fromEpochMilliseconds(timestampMillis),
        )

    @Test
    fun `opening a conversation with inbound messages sends a receipt naming the newest one`() =
        runTest(testDispatcher) {
            val newestInbound = message(id = "msg-newest", senderId = "user-peer", timestampMillis = 3_000L)
            every { messageRepository.observeMessages(conversationId) } returns
                flowOf(
                    listOf(
                        message(id = "msg-oldest", senderId = "user-peer", timestampMillis = 1_000L),
                        newestInbound,
                        message(id = "msg-mine", senderId = currentUserId, timestampMillis = 2_500L),
                    ),
                )

            newViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) { sendReadReceiptUseCase(conversationId, "msg-newest") }
        }

    @Test
    fun `an empty conversation sends no receipt`() =
        runTest(testDispatcher) {
            every { messageRepository.observeMessages(conversationId) } returns flowOf(emptyList())

            newViewModel()
            advanceUntilIdle()

            coVerify(exactly = 0) { sendReadReceiptUseCase(any(), any()) }
        }

    @Test
    fun `a conversation with only outbound messages sends no receipt`() =
        runTest(testDispatcher) {
            every { messageRepository.observeMessages(conversationId) } returns
                flowOf(
                    listOf(
                        message(id = "msg-mine-1", senderId = currentUserId, timestampMillis = 1_000L),
                        message(id = "msg-mine-2", senderId = currentUserId, timestampMillis = 2_000L),
                    ),
                )

            newViewModel()
            advanceUntilIdle()

            coVerify(exactly = 0) { sendReadReceiptUseCase(any(), any()) }
        }

    @Test
    fun `markAsRead still zeroes the unread count independent of receipt sending`() =
        runTest(testDispatcher) {
            every { messageRepository.observeMessages(conversationId) } returns flowOf(emptyList())

            newViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) { messageRepository.markAsRead(conversationId) }
        }

    /**
     * Regression: the unread-count write is an unsupervised coroutine too,
     * so a DB failure in it crashes the process rather than just leaving a
     * stale badge. The receipt is unaffected — the two run in separate
     * coroutines.
     */
    @Test
    fun `a failing markAsRead does not crash and leaves the receipt unaffected`() =
        runTest(testDispatcher) {
            every { messageRepository.observeMessages(conversationId) } returns
                flowOf(listOf(message(id = "msg-in", senderId = "user-peer", timestampMillis = 1_000L)))
            coEvery { messageRepository.markAsRead(any()) } throws IllegalStateException("DB closed during logout")

            newViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) { sendReadReceiptUseCase(conversationId, "msg-in") }
        }

    /**
     * Regression: resolving the newest inbound message runs in an
     * unsupervised [androidx.lifecycle.viewModelScope] coroutine, so an
     * exception escaping it reaches the scope's handler and takes down the
     * process. `runTest` surfaces exactly that — it captures uncaught
     * coroutine exceptions and fails the test — so these two cases fail
     * without the guard in `sendReadReceiptForNewestInboundMessage`.
     */
    @Test
    fun `a failing message flow loses the receipt without crashing`() =
        runTest(testDispatcher) {
            every { messageRepository.observeMessages(conversationId) } returns
                flow { throw IllegalStateException("DB closed during logout") }

            newViewModel()
            advanceUntilIdle()

            coVerify(exactly = 0) { sendReadReceiptUseCase(any(), any()) }
            coVerify(exactly = 1) { messageRepository.markAsRead(conversationId) }
        }

    /**
     * A flow that completes without ever emitting is distinct from one
     * emitting an empty list: `first()` throws NoSuchElementException rather
     * than returning `emptyList()`, so a `catch` operator upstream of it
     * would not help.
     */
    @Test
    fun `a message flow that completes without emitting loses the receipt without crashing`() =
        runTest(testDispatcher) {
            every { messageRepository.observeMessages(conversationId) } returns emptyFlow()

            newViewModel()
            advanceUntilIdle()

            coVerify(exactly = 0) { sendReadReceiptUseCase(any(), any()) }
            coVerify(exactly = 1) { messageRepository.markAsRead(conversationId) }
        }

    @Test
    fun `a message arriving while the chat is open marks it read again, an outbound one does not`() =
        runTest(testDispatcher) {
            val stream = MutableSharedFlow<List<Message>>(replay = 1)
            every { messageRepository.observeMessages(conversationId) } returns stream
            stream.emit(listOf(message(id = "m1", senderId = "user-peer", timestampMillis = 1_000L)))

            newViewModel()
            advanceUntilIdle()
            coVerify(exactly = 1) { messageRepository.markAsRead(conversationId) }

            stream.emit(
                listOf(
                    message(id = "m1", senderId = "user-peer", timestampMillis = 1_000L),
                    message(id = "mine", senderId = currentUserId, timestampMillis = 2_000L),
                ),
            )
            advanceUntilIdle()
            coVerify(exactly = 1) { messageRepository.markAsRead(conversationId) }

            stream.emit(
                listOf(
                    message(id = "m1", senderId = "user-peer", timestampMillis = 1_000L),
                    message(id = "m2", senderId = "user-peer", timestampMillis = 3_000L),
                ),
            )
            advanceUntilIdle()
            coVerify(exactly = 2) { messageRepository.markAsRead(conversationId) }
        }
}
