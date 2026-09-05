package com.sanchr.feature.chats

import androidx.lifecycle.SavedStateHandle
import com.sanchr.core.common.Result
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.ContactCard
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.core.model.MessageStatus
import com.sanchr.core.notifications.NotificationHandler
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
    private val sendMessageUseCase = mockk<SendMessageUseCase>()
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
            sendAttachmentUseCase = mockk(relaxed = true),
            attachmentDownloader = mockk(relaxed = true),
            sendReadReceiptUseCase = mockk<SendReadReceiptUseCase>(relaxed = true),
            presenceStore = PresenceStore(),
            sessionManager = mockk<SessionManager> { every { getUserId() } returns "self" },
            realtimeManager = realtimeManager,
            notificationHandler = mockk<NotificationHandler>(relaxed = true),
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
}
