package com.sanchr.feature.chats

import androidx.lifecycle.SavedStateHandle
import com.sanchr.core.common.Result
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.model.ChatVaultPolicy
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.ConversationType
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.PresenceStore
import com.sanchr.domain.messaging.SendAlbumUseCase
import com.sanchr.domain.messaging.SendAttachmentUseCase
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant

/**
 * A chat's vault policy reaches the sends made from it and the viewer opened
 * in it. Without that the switches on the Vault Media screen would be a
 * record of what someone asked for and nothing else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatDetailViewModelVaultPolicyTest {
    private val dispatcher = StandardTestDispatcher()
    private val conversationId = "conv-1"
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val realtimeManager = mockk<RealtimeManager>(relaxed = true)
    private val sendAttachment = mockk<SendAttachmentUseCase>()
    private val sendAlbum = mockk<SendAlbumUseCase>()
    private val sent = mutableListOf<AttachmentUploader.Prepared>()
    private var accountWideProtection = false

    private fun conversation(policy: ChatVaultPolicy) =
        Conversation(
            id = conversationId,
            type = ConversationType.DIRECT,
            participants = emptyList(),
            vaultPolicy = policy,
            updatedAt = Instant.fromEpochMilliseconds(1),
            createdAt = Instant.fromEpochMilliseconds(0),
        )

    private fun prepared(
        mimeType: String,
        name: String = "f",
    ) = AttachmentUploader.Prepared(bytes = byteArrayOf(1), mimeType = mimeType, fileName = name)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { realtimeManager.typingCache } returns MutableStateFlow(emptyMap())
        every { messageRepository.observeRecentMessages(any(), any()) } returns flowOf(emptyList())
        coEvery { messageRepository.oneToOneRecipient(any(), any()) } returns "peer"
        coEvery { messageRepository.draftText(any()) } returns null
        coEvery { sendAttachment(any(), capture(sent), any(), any()) } returns Result.Success(mockk(relaxed = true))
        coEvery { sendAlbum(any(), any(), any(), any()) } returns Result.Success(mockk(relaxed = true))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(policy: ChatVaultPolicy): ChatDetailViewModel {
        every { messageRepository.observeConversation(any()) } returns flowOf(conversation(policy))
        return ChatDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("conversationId" to conversationId)),
            messageRepository = messageRepository,
            sendMessageUseCase = mockk(relaxed = true),
            sendAttachmentUseCase = sendAttachment,
            sendAlbumUseCase = sendAlbum,
            attachmentDownloader = mockk(relaxed = true),
            videoCompressor = mockk(relaxed = true),
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
                    every { screenshotProtectionEnabled } returns flowOf(accountWideProtection)
                    every { screenSecurityActive } returns flowOf(accountWideProtection)
                    every { chatWallpaper } returns flowOf("default")
                    every { mediaAutoDownload } returns flowOf("wifi")
                    every { typingIndicatorsEnabled } returns flowOf(false)
                    every { canSendTypingIndicators } returns flowOf(false)
                },
            linkPreviewFetcher = mockk(relaxed = true),
            safetyNumbers = mockk(relaxed = true),
            connectivityMonitor = mockk(relaxed = true),
            appContext = mockk(relaxed = true),
        )
    }

    @Test
    fun `a photo sent from a view-once chat goes view-once`() =
        runTest(dispatcher) {
            val viewModel = viewModel(ChatVaultPolicy(viewOnceOutgoing = true))
            advanceUntilIdle()

            viewModel.sendAttachment(prepared("image/jpeg"))
            advanceUntilIdle()

            assertEquals(listOf<Boolean?>(true), sent.map { it.isViewOnce })
        }

    @Test
    fun `a photo sent from an ordinary chat is an ordinary photo`() =
        runTest(dispatcher) {
            val viewModel = viewModel(ChatVaultPolicy.DEFAULT)
            advanceUntilIdle()

            viewModel.sendAttachment(prepared("image/jpeg"))
            advanceUntilIdle()

            assertEquals(listOf<Boolean?>(null), sent.map { it.isViewOnce })
        }

    @Test
    fun `a set from a view-once chat goes as separate view-once messages, not an album`() =
        runTest(dispatcher) {
            val viewModel = viewModel(ChatVaultPolicy(viewOnceOutgoing = true))
            advanceUntilIdle()

            viewModel.sendAttachments(listOf(prepared("image/jpeg", "a"), prepared("video/mp4", "b")))
            advanceUntilIdle()

            assertEquals(listOf("a", "b"), sent.map { it.fileName })
            assertTrue(sent.all { it.isViewOnce == true })
            coVerify(exactly = 0) { sendAlbum(any(), any(), any(), any()) }
        }

    @Test
    fun `a set from an ordinary chat is still one album`() =
        runTest(dispatcher) {
            val viewModel = viewModel(ChatVaultPolicy.DEFAULT)
            advanceUntilIdle()

            viewModel.sendAttachments(listOf(prepared("image/jpeg", "a"), prepared("image/jpeg", "b")))
            advanceUntilIdle()

            coVerify(exactly = 1) { sendAlbum(conversationId, any(), any(), any()) }
            assertTrue(sent.isEmpty())
        }

    @Test
    fun `a failed item stops the set and is reported`() =
        runTest(dispatcher) {
            coEvery { sendAttachment(any(), capture(sent), any(), any()) } returns Result.Error(IllegalStateException("offline"))
            val viewModel = viewModel(ChatVaultPolicy(viewOnceOutgoing = true))
            advanceUntilIdle()

            viewModel.sendAttachments(listOf(prepared("image/jpeg", "a"), prepared("image/jpeg", "b")))
            advanceUntilIdle()

            assertEquals(listOf("a"), sent.map { it.fileName })
            assertEquals("offline", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isSending)
        }

    @Test
    fun `the chat's own capture protection secures the viewer when the account-wide switch is off`() =
        runTest(dispatcher) {
            accountWideProtection = false

            val protected = viewModel(ChatVaultPolicy(captureProtection = true))
            advanceUntilIdle()
            assertTrue(protected.uiState.value.screenshotProtectionEnabled)

            val ordinary = viewModel(ChatVaultPolicy.DEFAULT)
            advanceUntilIdle()
            assertFalse(ordinary.uiState.value.screenshotProtectionEnabled)
        }

    @Test
    fun `the account-wide switch still protects a chat with no policy of its own`() =
        runTest(dispatcher) {
            accountWideProtection = true

            val viewModel = viewModel(ChatVaultPolicy.DEFAULT)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.screenshotProtectionEnabled)
        }
}
