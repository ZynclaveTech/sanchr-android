package com.sanchr.feature.chats

import androidx.work.WorkManager
import app.cash.turbine.test
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.ConversationType
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.ObserveConversationsUseCase
import com.sanchr.sync.SyncState
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

/**
 * Hiding a chat is device-local and reversible: nothing is deleted and the
 * server is never told, so a restored chat comes back whole.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatsListViewModelHiddenTest {
    private val testDispatcher = StandardTestDispatcher()
    private val observeConversationsUseCase = mockk<ObserveConversationsUseCase>()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val contactRepository = mockk<ContactRepository>(relaxed = true)
    private val syncState = mockk<SyncState>(relaxed = true)

    private fun conversation(
        id: String,
        title: String,
    ) = Conversation(
        id = id,
        type = ConversationType.DIRECT,
        participants = emptyList(),
        title = title,
        updatedAt = Instant.fromEpochMilliseconds(1),
        createdAt = Instant.fromEpochMilliseconds(0),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { observeConversationsUseCase() } returns emptyFlow()
        coEvery { syncState.isSyncing } returns MutableStateFlow(false)
        coEvery { contactRepository.observeRegisteredContacts() } returns flowOf(emptyList())
        every { messageRepository.observeArchivedConversations() } returns flowOf(emptyList())
        every { messageRepository.observeHiddenConversations() } returns flowOf(listOf(conversation("c9", "Ravi Kumar")))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel() =
        ChatsListViewModel(
            observeConversationsUseCase = observeConversationsUseCase,
            workManager = mockk<WorkManager>(relaxed = true),
            contactRepository = contactRepository,
            messageRepository = messageRepository,
            // Not exercised here: these tests cover listing, actions and the new-chat
            // picker, none of which send an attachment.
            sendAttachmentUseCase = mockk(relaxed = true),
            syncState = syncState,
            sessionManager = mockk(relaxed = true),
            realtimeManager = mockk(relaxed = true) { every { typingCache } returns MutableStateFlow(emptyMap()) },
        )

    @Test
    fun `the hidden list is what the store reports`() =
        runTest(testDispatcher) {
            val model = newViewModel()

            // Collected, not read: the flow is WhileSubscribed, so its value
            // stays at the initial empty list until something listens.
            model.hidden.test {
                assertEquals(emptyList(), awaitItem())
                assertEquals(listOf("c9"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `hiding a chat persists the flag and deletes nothing`() =
        runTest(testDispatcher) {
            val model = newViewModel()

            model.setHidden(conversation("c1", "Ravi Kumar"), hidden = true)
            advanceUntilIdle()

            coVerify(exactly = 1) { messageRepository.setHidden("c1", true) }
            coVerify(exactly = 0) { messageRepository.deleteConversation(any()) }
        }

    @Test
    fun `restoring a chat clears the flag rather than re-creating anything`() =
        runTest(testDispatcher) {
            val model = newViewModel()

            model.setHidden(conversation("c1", "Ravi Kumar"), hidden = false)
            advanceUntilIdle()

            coVerify(exactly = 1) { messageRepository.setHidden("c1", false) }
        }

    @Test
    fun `a store failure surfaces as an action error instead of crashing the list`() =
        runTest(testDispatcher) {
            coEvery { messageRepository.setHidden(any(), any()) } throws IllegalStateException("db closed")
            val model = newViewModel()

            model.setHidden(conversation("c1", "Ravi Kumar"), hidden = true)
            advanceUntilIdle()

            assertEquals("db closed", model.actionError.value)
        }

    @Test
    fun `a failing hidden stream leaves the list empty rather than tearing down the screen`() =
        runTest(testDispatcher) {
            every { messageRepository.observeHiddenConversations() } returns
                kotlinx.coroutines.flow.flow { throw IllegalStateException("db closed") }
            val model = newViewModel()

            model.hidden.test {
                assertEquals(emptyList(), awaitItem())
                expectNoEvents()
            }
        }
}
