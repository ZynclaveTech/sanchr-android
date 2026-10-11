package com.sanchr.feature.contacts

import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.proto.contacts.ContactServiceClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Tapping a contact has to open their *conversation*. It used to open the
 * chat screen with the contact's user id standing in for a conversation id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelOpenChatTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        ContactsViewModel(
            mockk<ContactServiceClient>(relaxed = true),
            mockk<ContactRepository>(relaxed = true),
            messageRepository,
        )

    @Test
    fun `tapping a contact opens the conversation with them, not their user id`() =
        runTest(dispatcher) {
            coEvery { messageRepository.ensureConversation("user-ada") } returns "conv-1"
            val vm = viewModel()
            val events = mutableListOf<ContactsEvent>()
            val collecting = launch { vm.events.collect(events::add) }

            vm.openChat("user-ada")

            assertEquals(listOf<ContactsEvent>(ContactsEvent.OpenConversation("conv-1")), events)
            collecting.cancel()
        }

    @Test
    fun `a conversation that cannot be made says so and opens nothing`() =
        runTest(dispatcher) {
            coEvery { messageRepository.ensureConversation(any()) } throws IllegalStateException("offline")
            val vm = viewModel()
            val events = mutableListOf<ContactsEvent>()
            val collecting = launch { vm.events.collect(events::add) }

            vm.openChat("user-ada")

            assertEquals(listOf<ContactsEvent>(ContactsEvent.Error("Couldn't open that chat")), events)
            collecting.cancel()
        }

    @Test
    fun `a second tap while the first is still opening opens one chat`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<String>()
            coEvery { messageRepository.ensureConversation("user-ada") } coAnswers { gate.await() }
            val vm = viewModel()
            val events = mutableListOf<ContactsEvent>()
            val collecting = launch { vm.events.collect(events::add) }

            vm.openChat("user-ada")
            vm.openChat("user-ada")
            gate.complete("conv-1")

            coVerify(exactly = 1) { messageRepository.ensureConversation(any()) }
            assertEquals(1, events.size)
            collecting.cancel()
        }

    @Test
    fun `after a failure the contact can be tapped again`() =
        runTest(dispatcher) {
            coEvery { messageRepository.ensureConversation("user-ada") } throws IllegalStateException("offline") andThen "conv-1"
            val vm = viewModel()
            val events = mutableListOf<ContactsEvent>()
            val collecting = launch { vm.events.collect(events::add) }

            vm.openChat("user-ada")
            vm.openChat("user-ada")

            assertEquals(ContactsEvent.OpenConversation("conv-1"), events.last())
            collecting.cancel()
        }
}
