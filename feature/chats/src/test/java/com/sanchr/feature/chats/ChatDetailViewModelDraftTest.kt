package com.sanchr.feature.chats

import androidx.lifecycle.SavedStateHandle
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
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
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Unsent composer text survives leaving the chat.
 *
 * It lived in view-model state alone, so backing out mid-sentence — or
 * having the process killed behind the photo picker — destroyed it. iOS has
 * saved and restored it all along.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatDetailViewModelDraftTest {
    private val dispatcher = StandardTestDispatcher()
    private val conversationId = "conv-1"
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val realtimeManager = mockk<RealtimeManager>(relaxed = true)
    private val userPreferences =
        mockk<UserPreferences>(relaxed = true) {
            every { linkPreviewsEnabled } returns flowOf(true)
            every { screenshotProtectionEnabled } returns flowOf(true)
            every { chatWallpaper } returns flowOf("default")
            every { mediaAutoDownload } returns flowOf("wifi")
            every { typingIndicatorsEnabled } returns flowOf(false)
        }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { messageRepository.observeConversation(any()) } returns emptyFlow()
        every { messageRepository.observeMessages(any()) } returns flowOf(emptyList())
        every { realtimeManager.typingCache } returns MutableStateFlow(emptyMap())
        coEvery { messageRepository.oneToOneRecipient(any(), any()) } returns "peer"
        coEvery { userPreferences.draft(conversationId) } returns ""
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
            userPreferences = userPreferences,
            linkPreviewFetcher = mockk(relaxed = true),
            safetyNumbers = mockk(relaxed = true),
            connectivityMonitor = mockk(relaxed = true),
            appContext = mockk(relaxed = true),
        )

    @Test
    fun `a stored draft is restored into the composer`() =
        runTest(dispatcher) {
            coEvery { userPreferences.draft(conversationId) } returns "half a sentence"

            val viewModel = newViewModel()
            advanceUntilIdle()

            assertEquals("half a sentence", viewModel.uiState.value.inputText)
        }

    @Test
    fun `typing is saved, once the keystrokes stop`() =
        runTest(dispatcher) {
            val viewModel = newViewModel()
            advanceUntilIdle()

            viewModel.onInputTextChanged("on my")
            viewModel.onInputTextChanged("on my way")
            advanceTimeBy(1_000)
            advanceUntilIdle()

            // Debounced: the intermediate keystroke is not a separate write.
            coVerify(exactly = 0) { userPreferences.setDraft(conversationId, "on my") }
            coVerify { userPreferences.setDraft(conversationId, "on my way") }
        }

    @Test
    fun `sending clears the draft, so it does not come back`() =
        runTest(dispatcher) {
            val viewModel = newViewModel()
            advanceUntilIdle()
            viewModel.onInputTextChanged("sent already")
            advanceTimeBy(1_000)

            viewModel.sendMessage()
            advanceUntilIdle()

            coVerify { userPreferences.setDraft(conversationId, "") }
            assertEquals("", viewModel.uiState.value.inputText)
        }
}
