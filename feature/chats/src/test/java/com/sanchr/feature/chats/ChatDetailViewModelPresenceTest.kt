package com.sanchr.feature.chats

import androidx.lifecycle.SavedStateHandle
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.notifications.NotificationHandler
import com.sanchr.domain.messaging.ForwardMessageUseCase
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.PresenceStatus
import com.sanchr.domain.messaging.PresenceStore
import com.sanchr.domain.messaging.SendMessageUseCase
import com.sanchr.domain.messaging.SendReadReceiptUseCase
import com.sanchr.sync.realtime.RealtimeManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain

/** The chat header's presence line: track the 1:1 peer, mirror the store, age it, and stop on clear. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatDetailViewModelPresenceTest {
    private val testDispatcher = StandardTestDispatcher()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val forwardMessageUseCase = mockk<ForwardMessageUseCase>(relaxed = true)
    private val realtimeManager = mockk<RealtimeManager>(relaxed = true)
    private var now = 1_700_000_000_000L
    private val presenceStore = PresenceStore { now }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { messageRepository.observeConversation(any()) } returns emptyFlow()
        every { messageRepository.observeMessages(any()) } returns flowOf(emptyList())
        every { realtimeManager.typingCache } returns MutableStateFlow(emptyMap())
        coEvery { messageRepository.oneToOneRecipient("conv-1", "self") } returns "peer"
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): ChatDetailViewModel =
        ChatDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("conversationId" to "conv-1")),
            messageRepository = messageRepository,
            sendMessageUseCase = mockk<SendMessageUseCase>(),
            sendAttachmentUseCase = mockk(relaxed = true),
            attachmentDownloader = mockk(relaxed = true),
            sendReadReceiptUseCase = mockk<SendReadReceiptUseCase>(relaxed = true),
            toggleReactionUseCase = mockk(relaxed = true),
            consumeViewOnceUseCase = mockk(relaxed = true),
            forwardMessageUseCase = forwardMessageUseCase,
            presenceStore = presenceStore,
            sessionManager = mockk<SessionManager> { every { getUserId() } returns "self" },
            realtimeManager = realtimeManager,
            notificationHandler = mockk<NotificationHandler>(relaxed = true),
        )

    @Test
    fun `opening a 1-1 chat tracks the peer and shows Online until it decays`() =
        kotlinx.coroutines.test.runTest(testDispatcher) {
            val vm = newViewModel()
            runCurrent()
            verify { realtimeManager.trackPresencePeer("peer") }
            assertNull(vm.uiState.value.peerPresence)

            presenceStore.update("peer", PresenceStatus.ONLINE, null)
            runCurrent()
            assertEquals("Online", vm.uiState.value.peerPresence)

            now += PresenceStore.ONLINE_TTL_MS + 1
            advanceTimeBy(16_000)
            runCurrent()
            assertNull(vm.uiState.value.peerPresence)

            presenceStore.update("peer", PresenceStatus.OFFLINE, now - 120_000)
            runCurrent()
            assertEquals("Last seen 2 min ago", vm.uiState.value.peerPresence)
            presenceStore.update("peer", PresenceStatus.HIDDEN, now)
            runCurrent()
            assertNull(vm.uiState.value.peerPresence)
        }
}
