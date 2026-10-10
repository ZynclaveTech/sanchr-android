package com.sanchr.feature.chats

import androidx.lifecycle.SavedStateHandle
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.core.model.MessageStatus
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.PresenceStore
import com.sanchr.sync.realtime.RealtimeManager
import io.mockk.coEvery
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant

/**
 * A message's expiry has to reach the UI model.
 *
 * Media Auto-Save refuses to copy disappearing media to the gallery, and it
 * reads the flag off the row the bubble was rendered from. Looking the expiry
 * up in the view model's own cache instead would fail open the moment that
 * cache no longer held the row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatDetailViewModelEphemeralMappingTest {
    private val testDispatcher = StandardTestDispatcher()
    private val conversationId = "conv-1"
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val realtimeManager = mockk<RealtimeManager>(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { messageRepository.observeConversation(any()) } returns emptyFlow()
        every { realtimeManager.typingCache } returns MutableStateFlow(emptyMap())
        coEvery { messageRepository.oneToOneRecipient(any(), any()) } returns "peer"
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun message(expiresAt: Instant?) =
        Message(
            id = "msg-1",
            conversationId = conversationId,
            senderId = "peer",
            content = MessageContent.Text(body = "hi"),
            status = MessageStatus.DELIVERED,
            timestamp = Instant.fromEpochMilliseconds(1_000L),
            expiresAt = expiresAt,
        )

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
                mockk {
                    every { linkPreviewsEnabled } returns flowOf(true)
                    every { screenshotProtectionEnabled } returns flowOf(true)
                    every { chatWallpaper } returns flowOf("default")
                    every { mediaAutoDownload } returns flowOf("wifi")
                    every { typingIndicatorsEnabled } returns flowOf(true)
                },
            linkPreviewFetcher = mockk(relaxed = true),
            safetyNumbers = mockk(relaxed = true),
            connectivityMonitor = mockk(relaxed = true),
            appContext = mockk(relaxed = true),
        )

    @Test
    fun `a message with an expiry is marked ephemeral`() =
        runTest(testDispatcher) {
            every { messageRepository.observeMessages(conversationId) } returns
                flowOf(listOf(message(expiresAt = Instant.fromEpochMilliseconds(9_000L))))

            val viewModel = newViewModel()
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.messages
                    .single()
                    .isEphemeral,
            )
        }

    @Test
    fun `a message without an expiry is not marked ephemeral`() =
        runTest(testDispatcher) {
            every { messageRepository.observeMessages(conversationId) } returns
                flowOf(listOf(message(expiresAt = null)))

            val viewModel = newViewModel()
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.messages
                    .single()
                    .isEphemeral,
            )
        }
}
