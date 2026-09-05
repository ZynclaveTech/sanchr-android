package com.sanchr.feature.chats

import androidx.lifecycle.SavedStateHandle
import com.sanchr.core.common.Result
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.ContactCard
import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.core.model.MessageReaction
import com.sanchr.core.model.MessageStatus
import com.sanchr.core.notifications.NotificationHandler
import com.sanchr.domain.messaging.ForwardMessageUseCase
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.PresenceStore
import com.sanchr.domain.messaging.SendAttachmentUseCase
import com.sanchr.domain.messaging.SendMessageUseCase
import com.sanchr.domain.messaging.SendReadReceiptUseCase
import com.sanchr.domain.messaging.media.AttachmentUploader
import com.sanchr.sync.realtime.RealtimeManager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant

/** Contact cards go out as iOS's bare `{"name","phoneNumber"}` body and come back as a contact row. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatDetailViewModelContactTest {
    private val testDispatcher = StandardTestDispatcher()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val forwardMessageUseCase = mockk<ForwardMessageUseCase>(relaxed = true)
    private val sendMessageUseCase = mockk<SendMessageUseCase>()
    private val sendAttachmentUseCase = mockk<SendAttachmentUseCase>(relaxed = true)
    private val realtimeManager = mockk<RealtimeManager>(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { messageRepository.observeConversation(any()) } returns emptyFlow()
        every { realtimeManager.typingCache } returns MutableStateFlow(emptyMap())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): ChatDetailViewModel =
        ChatDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("conversationId" to "conv-1")),
            messageRepository = messageRepository,
            sendMessageUseCase = sendMessageUseCase,
            sendAttachmentUseCase = sendAttachmentUseCase,
            attachmentDownloader = mockk(relaxed = true),
            sendReadReceiptUseCase = mockk<SendReadReceiptUseCase>(relaxed = true),
            toggleReactionUseCase = mockk(relaxed = true),
            consumeViewOnceUseCase = mockk(relaxed = true),
            forwardMessageUseCase = forwardMessageUseCase,
            presenceStore = PresenceStore(),
            sessionManager = mockk<SessionManager> { every { getUserId() } returns "self" },
            realtimeManager = realtimeManager,
            notificationHandler = mockk<NotificationHandler>(relaxed = true),
            userPreferences = mockk { every { linkPreviewsEnabled } returns flowOf(true) },
            linkPreviewFetcher = mockk(relaxed = true),
        )

    @Test
    fun `sendContact sends the wire body with the contact content type`() =
        runTest(testDispatcher) {
            every { messageRepository.observeMessages(any()) } returns flowOf(emptyList())
            coEvery { sendMessageUseCase(any(), any(), any()) } returns Result.Success(mockk(relaxed = true))
            val vm = newViewModel()

            vm.sendContact(ContactCard(name = "Ada", phoneNumber = "+15550100"))
            advanceUntilIdle()

            coVerify(exactly = 1) { sendMessageUseCase("conv-1", """{"name":"Ada","phoneNumber":"+15550100"}""", "contact") }
            assertEquals(false, vm.uiState.value.isSending)
        }

    @Test
    fun `a contact row exposes the card to the bubble`() =
        runTest(testDispatcher) {
            every { messageRepository.observeMessages(any()) } returns
                flowOf(
                    listOf(
                        Message(
                            id = "m1",
                            conversationId = "conv-1",
                            senderId = "peer",
                            content = MessageContent.Contact(name = "Ada", phoneNumber = "+15550100"),
                            status = MessageStatus.DELIVERED,
                            timestamp = Instant.fromEpochMilliseconds(1_000),
                        ),
                    ),
                )
            val vm = newViewModel()
            advanceUntilIdle()

            val row =
                vm.uiState.value.messages
                    .single()
            assertEquals("contact", row.contentType)
            assertEquals(ContactCard("Ada", "+15550100"), row.contact)
            assertEquals("Ada", row.text)
        }

    @Test
    fun `reactions are grouped into chips with the viewer's own marked`() =
        runTest(testDispatcher) {
            every { messageRepository.observeMessages(any()) } returns
                flowOf(
                    listOf(
                        Message(
                            id = "m1",
                            conversationId = "conv-1",
                            senderId = "peer",
                            content = MessageContent.Text("hi"),
                            status = MessageStatus.DELIVERED,
                            timestamp = Instant.fromEpochMilliseconds(1_000),
                            reactions =
                                listOf(
                                    MessageReaction("❤️", "peer", Instant.fromEpochMilliseconds(1)),
                                    MessageReaction("❤️", "self", Instant.fromEpochMilliseconds(2)),
                                    MessageReaction("👍", "peer", Instant.fromEpochMilliseconds(3)),
                                ),
                        ),
                    ),
                )
            val vm = newViewModel()
            advanceUntilIdle()

            assertEquals(
                listOf(ReactionChip("❤️", 2, mine = true), ReactionChip("👍", 1, mine = false)),
                vm.uiState.value.messages
                    .single()
                    .reactions,
            )
        }

    @Test
    fun `a reply resolves its quote from the transcript and a missing quoted row leaves it null`() =
        runTest(testDispatcher) {
            fun msg(
                id: String,
                sender: String,
                text: String,
                replyTo: String? = null,
            ) = Message(
                id = id,
                conversationId = "conv-1",
                senderId = sender,
                content = MessageContent.Text(text),
                status = MessageStatus.DELIVERED,
                timestamp = Instant.fromEpochMilliseconds(1_000),
                replyToId = replyTo,
            )
            every { messageRepository.observeMessages(any()) } returns
                flowOf(
                    listOf(
                        msg("m1", "self", "see you at six"),
                        msg("m2", "peer", "yes", replyTo = "m1"),
                        msg("m3", "peer", "?", replyTo = "gone"),
                    ),
                )
            val vm = newViewModel()
            advanceUntilIdle()

            val rows = vm.uiState.value.messages
            assertEquals(ReplyQuote(authorName = "You", preview = "see you at six"), rows[1].quote)
            assertEquals("m1", rows[1].replyToId)
            assertEquals(null, rows[2].quote)
            assertEquals("gone", rows[2].replyToId)

            vm.setReply(rows[0])
            assertEquals(
                "m1",
                vm.uiState.value.replyingTo
                    ?.id,
            )
            vm.clearReply()
            assertEquals(null, vm.uiState.value.replyingTo)
        }

    @Test
    fun `view-once media is flagged for the secure viewer and a tombstone reads Viewed`() =
        runTest(testDispatcher) {
            val once = MediaAttachment(url = "sanchr-media://m", mimeType = "image/jpeg", isViewOnce = true)
            every { messageRepository.observeMessages(any()) } returns
                flowOf(
                    listOf(
                        Message(
                            id = "m1",
                            conversationId = "conv-1",
                            senderId = "peer",
                            content = MessageContent.Image(url = once.url, thumbnailUrl = null, width = 1, height = 1, attachment = once),
                            status = MessageStatus.DELIVERED,
                            timestamp = Instant.fromEpochMilliseconds(1_000),
                        ),
                        Message(
                            id = "m2",
                            conversationId = "conv-1",
                            senderId = "peer",
                            content = MessageContent.System(MessageContent.System.VIEW_ONCE_CONSUMED),
                            status = MessageStatus.DELIVERED,
                            timestamp = Instant.fromEpochMilliseconds(2_000),
                        ),
                    ),
                )
            val vm = newViewModel()
            advanceUntilIdle()

            val rows = vm.uiState.value.messages
            assertEquals(true, rows[0].isViewOnce)
            assertEquals("system", rows[1].contentType)
            assertEquals("Viewed", rows[1].text)
        }

    @Test
    fun `forward hands the domain row to the use case and reports a notice, and delete for everyone only applies to own rows`() =
        runTest(testDispatcher) {
            val row =
                Message(
                    id = "m1",
                    conversationId = "conv-1",
                    senderId = "peer",
                    content = MessageContent.Text("hi"),
                    status = MessageStatus.DELIVERED,
                    timestamp = Instant.fromEpochMilliseconds(1_000),
                )
            every { messageRepository.observeMessages(any()) } returns flowOf(listOf(row))
            coEvery { forwardMessageUseCase(row, listOf("c2", "c3")) } returns ForwardMessageUseCase.Outcome(sent = 2, failed = 0)
            val vm = newViewModel()
            advanceUntilIdle()

            vm.forward(
                vm.uiState.value.messages
                    .single(),
                listOf("c2", "c3"),
            )
            advanceUntilIdle()
            assertEquals("Forwarded to 2 chats", vm.uiState.value.notice)

            vm.deleteMessage(
                vm.uiState.value.messages
                    .single(),
                forEveryone = true,
            )
            advanceUntilIdle()
            coVerify { messageRepository.deleteMessage("m1", forEveryone = false) }
        }

    @Test
    fun `search debounces, keeps only the latest query's results, and steps with wrap-around`() =
        runTest(testDispatcher) {
            every { messageRepository.observeMessages(any()) } returns flowOf(emptyList())
            coEvery { messageRepository.searchMessages("conv-1", "hel") } returns listOf("m3", "m1")
            coEvery { messageRepository.searchMessages("conv-1", "hello") } returns listOf("m3")
            val vm = newViewModel()
            advanceUntilIdle()

            vm.openSearch()
            vm.onSearchQueryChanged("hel")
            vm.onSearchQueryChanged("hello")
            advanceUntilIdle()
            coVerify(exactly = 0) { messageRepository.searchMessages("conv-1", "hel") }
            assertEquals(
                listOf("m3"),
                vm.uiState.value.search
                    ?.resultIds,
            )

            vm.onSearchQueryChanged("hel")
            advanceUntilIdle()
            assertEquals(
                "m3",
                vm.uiState.value.search
                    ?.currentId,
            )
            vm.nextSearchResult()
            assertEquals(
                "m1",
                vm.uiState.value.search
                    ?.currentId,
            )
            vm.nextSearchResult()
            assertEquals(
                "m3",
                vm.uiState.value.search
                    ?.currentId,
            )
            vm.previousSearchResult()
            assertEquals(
                "m1",
                vm.uiState.value.search
                    ?.currentId,
            )

            vm.closeSearch()
            assertEquals(null, vm.uiState.value.search)
        }

    @Test
    fun `a pending reply is consumed by whichever send happens next, attachment or card`() =
        runTest(testDispatcher) {
            val quoted =
                Message(
                    id = "m1",
                    conversationId = "conv-1",
                    senderId = "peer",
                    content = MessageContent.Text("hi"),
                    status = MessageStatus.DELIVERED,
                    timestamp = Instant.fromEpochMilliseconds(1_000),
                )
            every { messageRepository.observeMessages(any()) } returns flowOf(listOf(quoted))
            coEvery { sendAttachmentUseCase(any(), any(), any()) } returns Result.Success(mockk(relaxed = true))
            coEvery { sendMessageUseCase(any(), any(), any(), any()) } returns Result.Success(mockk(relaxed = true))
            val vm = newViewModel()
            advanceUntilIdle()

            vm.setReply(
                vm.uiState.value.messages
                    .single(),
            )
            vm.sendAttachment(AttachmentUploader.Prepared(byteArrayOf(1), "image/jpeg", "p.jpg"))
            advanceUntilIdle()
            coVerify { sendAttachmentUseCase("conv-1", any(), "m1") }
            assertEquals(null, vm.uiState.value.replyingTo, "the banner clears once the send takes it")

            // Nothing pending now: the next send quotes nothing.
            vm.sendContact(ContactCard(name = "Ada", phoneNumber = "+15550100"))
            advanceUntilIdle()
            coVerify { sendMessageUseCase("conv-1", any(), "contact", null) }
        }
}
