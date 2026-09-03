package com.sanchr.feature.chats

import androidx.work.WorkManager
import app.cash.turbine.test
import com.sanchr.core.model.User
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.ObserveConversationsUseCase
import com.sanchr.sync.SyncState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant

/**
 * Unit tests for the new-chat bottom-sheet state machine on
 * [ChatsListViewModel]. The rest of the VM (conversation observation,
 * search, pull-to-refresh) is covered elsewhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatsListViewModelNewChatTest {
    private val testDispatcher = StandardTestDispatcher()

    private val observeConversationsUseCase = mockk<ObserveConversationsUseCase>()
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val contactRepository = mockk<ContactRepository>()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val syncState = mockk<SyncState>(relaxed = true)

    private val fakeUser =
        User(
            id = "user-42",
            phoneNumber = "+15551234567",
            displayName = "Ada",
            createdAt = Instant.fromEpochMilliseconds(0),
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // VM's upstream flows are wired via combine; we don't need live data for
        // the new-chat tests — an empty flow is enough.
        coEvery { observeConversationsUseCase() } returns emptyFlow()
        coEvery { syncState.isSyncing } returns kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): ChatsListViewModel =
        ChatsListViewModel(
            observeConversationsUseCase = observeConversationsUseCase,
            workManager = workManager,
            contactRepository = contactRepository,
            messageRepository = messageRepository,
            syncState = syncState,
        )

    @Test
    fun `openNewChat sets isOpen = true`() =
        runTest(testDispatcher) {
            val vm = newViewModel()
            vm.openNewChat()
            assertTrue(vm.newChat.value.isOpen)
            assertEquals("", vm.newChat.value.phone)
        }

    @Test
    fun `submitNewChat with invalid phone sets INVALID_PHONE error`() =
        runTest(testDispatcher) {
            val vm = newViewModel()
            vm.openNewChat()
            vm.onNewChatPhoneChanged("not-a-phone")
            vm.submitNewChat()
            advanceUntilIdle()

            assertEquals(NewChatError.INVALID_PHONE, vm.newChat.value.error)
            assertTrue(vm.newChat.value.isOpen)
            assertEquals(false, vm.newChat.value.isSubmitting)
        }

    @Test
    fun `submitNewChat with NOT_FOUND sets NOT_FOUND error`() =
        runTest(testDispatcher) {
            coEvery { contactRepository.lookupByPhone("+15551234567") } returns null
            val vm = newViewModel()
            vm.openNewChat()
            vm.onNewChatPhoneChanged("+15551234567")
            vm.submitNewChat()
            advanceUntilIdle()

            assertEquals(NewChatError.NOT_FOUND, vm.newChat.value.error)
            assertEquals(false, vm.newChat.value.isSubmitting)
        }

    @Test
    fun `submitNewChat with server error sets SERVER_ERROR`() =
        runTest(testDispatcher) {
            coEvery { contactRepository.lookupByPhone(any()) } throws RuntimeException("boom")
            val vm = newViewModel()
            vm.openNewChat()
            vm.onNewChatPhoneChanged("+15551234567")
            vm.submitNewChat()
            advanceUntilIdle()

            assertEquals(NewChatError.SERVER_ERROR, vm.newChat.value.error)
            assertEquals(false, vm.newChat.value.isSubmitting)
        }

    @Test
    fun `submitNewChat on success emits OpenConversation event and closes sheet`() =
        runTest(testDispatcher) {
            coEvery { contactRepository.lookupByPhone("+15551234567") } returns fakeUser
            coEvery { messageRepository.ensureConversation("user-42") } returns "conv-123"

            val vm = newViewModel()
            vm.events.test {
                vm.openNewChat()
                vm.onNewChatPhoneChanged("+15551234567")
                vm.submitNewChat()
                advanceUntilIdle()

                val event = awaitItem()
                assertIs<NewChatEvent.OpenConversation>(event)
                assertEquals("conv-123", event.conversationId)
                cancelAndIgnoreRemainingEvents()
            }
            // Sheet reset on success.
            assertEquals(NewChatState(), vm.newChat.value)
        }

    @Test
    fun `submitNewChat calls ensureConversation with user id from lookup`() =
        runTest(testDispatcher) {
            coEvery { contactRepository.lookupByPhone("+15551234567") } returns fakeUser
            coEvery { messageRepository.ensureConversation(any()) } returns "conv-1"

            val vm = newViewModel()
            vm.openNewChat()
            vm.onNewChatPhoneChanged("+15551234567")
            vm.submitNewChat()
            advanceUntilIdle()

            coVerify(exactly = 1) { messageRepository.ensureConversation("user-42") }
        }
}
