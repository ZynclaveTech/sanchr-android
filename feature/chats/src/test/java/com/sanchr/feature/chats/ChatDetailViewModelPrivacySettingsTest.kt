package com.sanchr.feature.chats

import androidx.lifecycle.SavedStateHandle
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.network.ConnectivityMonitor
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.PresenceStore
import com.sanchr.proto.messaging.TypingIndicator
import com.sanchr.sync.realtime.RealtimeManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
 * The privacy settings that used to persist and change nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatDetailViewModelPrivacySettingsTest {
    private val testDispatcher = StandardTestDispatcher()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val realtimeManager = mockk<RealtimeManager>(relaxed = true)
    private val connectivityMonitor = mockk<ConnectivityMonitor>(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { messageRepository.observeConversation(any()) } returns emptyFlow()
        every { messageRepository.observeMessages(any()) } returns flowOf(emptyList())
        every { realtimeManager.typingCache } returns MutableStateFlow(emptyMap())
        coEvery { messageRepository.oneToOneRecipient(any(), any()) } returns "peer"
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel(
        typingEnabled: Boolean = true,
        autoDownload: String = "wifi",
    ) = ChatDetailViewModel(
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
                every { chatWallpaper } returns flowOf("default")
                every { mediaAutoDownload } returns flowOf(autoDownload)
                every { typingIndicatorsEnabled } returns flowOf(typingEnabled)
            },
        linkPreviewFetcher = mockk(relaxed = true),
        safetyNumbers = mockk(relaxed = true),
        connectivityMonitor = connectivityMonitor,
    )

    @Test
    fun `typing is broadcast when the setting is on`() =
        runTest(testDispatcher) {
            val model = newViewModel(typingEnabled = true)

            model.onInputTextChanged("hi")
            runCurrent()

            verify(exactly = 1) { realtimeManager.sendTypingIndicator("conv-1", true) }
        }

    @Test
    fun `typing is not broadcast when the setting is off`() =
        runTest(testDispatcher) {
            val model = newViewModel(typingEnabled = false)

            model.onInputTextChanged("hi")
            runCurrent()

            verify(exactly = 0) { realtimeManager.sendTypingIndicator(any(), any()) }
        }

    @Test
    fun `turning typing off also stops showing the peer's, because it is a mutual courtesy`() =
        runTest(testDispatcher) {
            every { realtimeManager.typingCache } returns
                MutableStateFlow(
                    mapOf(
                        "conv-1" to TypingIndicator("conv-1", "peer", true),
                    ),
                )

            val on = newViewModel(typingEnabled = true)
            runCurrent()
            assertTrue(on.uiState.value.peerTyping)

            val off = newViewModel(typingEnabled = false)
            runCurrent()
            assertFalse(off.uiState.value.peerTyping)
        }

    @Test
    fun `wifi only holds media back on mobile data and allows it on wifi`() =
        runTest(testDispatcher) {
            every { connectivityMonitor.isMetered } returns true
            val metered = newViewModel(autoDownload = "wifi")
            runCurrent()
            assertFalse(metered.mayAutoDownload())

            every { connectivityMonitor.isMetered } returns false
            val unmetered = newViewModel(autoDownload = "wifi")
            runCurrent()
            assertTrue(unmetered.mayAutoDownload())
        }

    @Test
    fun `always downloads on mobile data and never holds back on wifi`() =
        runTest(testDispatcher) {
            every { connectivityMonitor.isMetered } returns true
            val always = newViewModel(autoDownload = "always")
            runCurrent()
            assertTrue(always.mayAutoDownload())

            every { connectivityMonitor.isMetered } returns false
            val never = newViewModel(autoDownload = "never")
            runCurrent()
            assertFalse(never.mayAutoDownload())
        }
}
