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
import kotlin.test.assertNull
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
 * Unit tests for the new-chat contact-picker state machine on
 * [ChatsListViewModel]. Mirrors the iOS NewChatContactPickerSheet contract:
 * a list of synced contacts plus a substring filter; tapping a row triggers
 * `messageRepository.ensureConversation` and emits an OpenConversation
 * navigation event. The rest of the VM (conversation observation, search,
 * pull-to-refresh) is covered elsewhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatsListViewModelNewChatTest {
    private val testDispatcher = StandardTestDispatcher()

    private val observeConversationsUseCase = mockk<ObserveConversationsUseCase>()
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val contactRepository = mockk<ContactRepository>(relaxed = true)
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val syncState = mockk<SyncState>(relaxed = true)

    private val ada =
        User(
            id = "user-ada",
            phoneNumber = "+15551234567",
            displayName = "Ada Lovelace",
            bio = "Mathematician",
            createdAt = Instant.fromEpochMilliseconds(0),
        )
    private val grace =
        User(
            id = "user-grace",
            phoneNumber = "+15557654321",
            displayName = "Grace Hopper",
            bio = null,
            createdAt = Instant.fromEpochMilliseconds(0),
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { observeConversationsUseCase() } returns emptyFlow()
        coEvery { syncState.isSyncing } returns MutableStateFlow(false)
        // Default contact-load mocks; individual tests override as needed.
        coEvery { contactRepository.observeRegisteredContacts() } returns flowOf(listOf(ada, grace))
        coEvery { contactRepository.syncContacts() } returns Unit
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
    fun `openNewChatPicker opens sheet and starts loading contacts`() =
        runTest(testDispatcher) {
            val vm = newViewModel()
            vm.openNewChatPicker()

            // Synchronous: sheet open + isLoading true before the load coroutine runs.
            assertTrue(vm.picker.value.isOpen)
            assertTrue(vm.picker.value.isLoading)
        }

    @Test
    fun `loadContacts on success populates contacts sorted by display name`() =
        runTest(testDispatcher) {
            val vm = newViewModel()
            vm.openNewChatPicker()
            advanceUntilIdle()

            val state = vm.picker.value
            assertEquals(false, state.isLoading)
            assertNull(state.error)
            // "Ada" < "Grace" alphabetically.
            assertEquals(listOf(ada, grace), state.contacts)
            assertEquals(listOf(ada, grace), state.filteredContacts)
        }

    @Test
    fun `loadContacts when observeRegisteredContacts throws sets error message`() =
        runTest(testDispatcher) {
            coEvery { contactRepository.observeRegisteredContacts() } throws
                RuntimeException("db unavailable")

            val vm = newViewModel()
            vm.openNewChatPicker()
            advanceUntilIdle()

            val state = vm.picker.value
            assertEquals(false, state.isLoading)
            assertEquals("db unavailable", state.error)
        }

    @Test
    fun `onPickerSearchQueryChanged filters by display name case-insensitively`() =
        runTest(testDispatcher) {
            val vm = newViewModel()
            vm.openNewChatPicker()
            advanceUntilIdle()

            vm.onPickerSearchQueryChanged("ADA")
            assertEquals(listOf(ada), vm.picker.value.filteredContacts)

            vm.onPickerSearchQueryChanged("hopper")
            assertEquals(listOf(grace), vm.picker.value.filteredContacts)
        }

    @Test
    fun `onPickerSearchQueryChanged filters by phone number substring`() =
        runTest(testDispatcher) {
            val vm = newViewModel()
            vm.openNewChatPicker()
            advanceUntilIdle()

            vm.onPickerSearchQueryChanged("7654321")
            assertEquals(listOf(grace), vm.picker.value.filteredContacts)
        }

    @Test
    fun `onPickerSearchQueryChanged filters by bio substring`() =
        runTest(testDispatcher) {
            val vm = newViewModel()
            vm.openNewChatPicker()
            advanceUntilIdle()

            vm.onPickerSearchQueryChanged("mathematician")
            assertEquals(listOf(ada), vm.picker.value.filteredContacts)
        }

    @Test
    fun `onPickerContactSelected emits OpenConversation event after ensureConversation`() =
        runTest(testDispatcher) {
            coEvery { messageRepository.ensureConversation("user-ada") } returns "conv-1"

            val vm = newViewModel()
            vm.openNewChatPicker()
            advanceUntilIdle()

            vm.events.test {
                vm.onPickerContactSelected(ada)
                advanceUntilIdle()

                val event = awaitItem()
                assertIs<NewChatEvent.OpenConversation>(event)
                assertEquals("conv-1", event.conversationId)
                cancelAndIgnoreRemainingEvents()
            }
            // Sheet resets on success.
            assertEquals(NewChatPickerState(), vm.picker.value)
            coVerify(exactly = 1) { messageRepository.ensureConversation("user-ada") }
        }

    @Test
    fun `onPickerContactSelected on ensureConversation failure surfaces error and keeps sheet open`() =
        runTest(testDispatcher) {
            coEvery { messageRepository.ensureConversation(any()) } throws
                RuntimeException("server down")

            val vm = newViewModel()
            vm.openNewChatPicker()
            advanceUntilIdle()

            vm.onPickerContactSelected(ada)
            advanceUntilIdle()

            val state = vm.picker.value
            assertTrue(state.isOpen)
            assertEquals("server down", state.error)
        }

    @Test
    fun `closeNewChatPicker resets state to closed defaults`() =
        runTest(testDispatcher) {
            val vm = newViewModel()
            vm.openNewChatPicker()
            advanceUntilIdle()
            vm.onPickerSearchQueryChanged("ada")

            vm.closeNewChatPicker()

            assertEquals(NewChatPickerState(), vm.picker.value)
        }
}
