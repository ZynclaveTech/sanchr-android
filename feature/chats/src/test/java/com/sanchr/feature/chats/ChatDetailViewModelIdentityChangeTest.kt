package com.sanchr.feature.chats

import androidx.lifecycle.SavedStateHandle
import com.sanchr.core.crypto.verify.SafetyNumberManager
import com.sanchr.core.datastore.SessionManager
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.PresenceStore
import com.sanchr.sync.realtime.RealtimeManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The banner reads the store rather than a cached flag. While a key change is
 * unreviewed, sends fail closed, so a stale "all clear" would leave the user
 * believing messages went out when they did not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatDetailViewModelIdentityChangeTest {
    private val testDispatcher = StandardTestDispatcher()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val realtimeManager = mockk<RealtimeManager>(relaxed = true)
    private val safetyNumbers = mockk<SafetyNumberManager>(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { messageRepository.observeConversation(any()) } returns emptyFlow()
        every { messageRepository.observeMessages(any()) } returns flowOf(emptyList())
        every { realtimeManager.typingCache } returns MutableStateFlow(emptyMap())
        coEvery { messageRepository.oneToOneRecipient("conv-1", "self") } returns "peer"
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel(): ChatDetailViewModel =
        ChatDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("conversationId" to "conv-1")),
            messageRepository = messageRepository,
            sendMessageUseCase = mockk(relaxed = true),
            sendAttachmentUseCase = mockk(relaxed = true),
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
                mockk {
                    every { linkPreviewsEnabled } returns flowOf(true)
                    every { screenshotProtectionEnabled } returns flowOf(true)
                },
            linkPreviewFetcher = mockk(relaxed = true),
            safetyNumbers = safetyNumbers,
        )

    @Test
    fun `an unreviewed key change raises the banner as soon as the peer is known`() =
        runTest(testDispatcher) {
            coEvery { safetyNumbers.hasPendingIdentityChange("peer") } returns true

            val model = newViewModel()
            runCurrent()

            assertTrue(model.uiState.value.identityChangePending)
        }

    @Test
    fun `no pending change leaves the banner down`() =
        runTest(testDispatcher) {
            coEvery { safetyNumbers.hasPendingIdentityChange("peer") } returns false

            val model = newViewModel()
            runCurrent()

            assertFalse(model.uiState.value.identityChangePending)
        }

    @Test
    fun `accepting the change lowers the banner without verifying the contact`() =
        runTest(testDispatcher) {
            coEvery { safetyNumbers.hasPendingIdentityChange("peer") } returnsMany listOf(true, false)
            val model = newViewModel()
            runCurrent()
            assertTrue(model.uiState.value.identityChangePending)

            model.acceptIdentityChange()
            runCurrent()

            coVerify(exactly = 1) { safetyNumbers.acceptIdentityChange("peer") }
            coVerify(exactly = 0) { safetyNumbers.markVerified(any(), any(), any()) }
            assertFalse(model.uiState.value.identityChangePending)
        }

    @Test
    fun `a store that throws leaves the banner down rather than crashing the chat`() =
        runTest(testDispatcher) {
            coEvery { safetyNumbers.hasPendingIdentityChange("peer") } throws IllegalStateException("db closed")

            val model = newViewModel()
            runCurrent()

            assertFalse(model.uiState.value.identityChangePending)
        }
}
