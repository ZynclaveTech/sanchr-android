package com.sanchr.feature.contacts.lookup

import com.sanchr.core.model.User
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.domain.messaging.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneLookupViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val contactRepository = mockk<ContactRepository>(relaxed = true)
    private val messageRepository = mockk<MessageRepository>(relaxed = true)

    private val ada =
        User(
            id = "user-ada",
            phoneNumber = "+15551234567",
            displayName = "Ada Lovelace",
            createdAt = Instant.fromEpochMilliseconds(0),
        )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PhoneLookupViewModel(contactRepository, messageRepository)

    @Test
    fun `too few digits cannot be searched`() {
        val model = viewModel()

        model.onPhoneNumberChanged("+1555")
        assertFalse(model.uiState.value.canSearch)

        model.onPhoneNumberChanged("+15551234567")
        assertTrue(model.uiState.value.canSearch)
    }

    @Test
    fun `a found user is offered`() =
        runTest(dispatcher) {
            coEvery { contactRepository.lookupByPhone("+15551234567") } returns ada
            val model = viewModel()
            model.onPhoneNumberChanged("+15551234567")

            model.search()

            assertEquals(LookupResult.Found(ada), model.uiState.value.result)
        }

    @Test
    fun `nobody with that number reads as not found`() =
        runTest(dispatcher) {
            coEvery { contactRepository.lookupByPhone(any()) } returns null
            val model = viewModel()
            model.onPhoneNumberChanged("+15551234567")

            model.search()

            assertEquals(LookupResult.NotFound, model.uiState.value.result)
        }

    @Test
    fun `a failed search is not reported as nobody having the number`() =
        runTest(dispatcher) {
            coEvery { contactRepository.lookupByPhone(any()) } throws IllegalStateException("offline")
            val model = viewModel()
            model.onPhoneNumberChanged("+15551234567")

            model.search()

            assertIs<LookupResult.Failed>(model.uiState.value.result)
        }

    @Test
    fun `editing the number clears a stale result`() =
        runTest(dispatcher) {
            coEvery { contactRepository.lookupByPhone(any()) } returns ada
            val model = viewModel()
            model.onPhoneNumberChanged("+15551234567")
            model.search()

            model.onPhoneNumberChanged("+15551234568")

            assertEquals(LookupResult.Idle, model.uiState.value.result)
        }

    @Test
    fun `messaging the found user opens their conversation`() =
        runTest(dispatcher) {
            coEvery { contactRepository.lookupByPhone(any()) } returns ada
            coEvery { messageRepository.ensureConversation("user-ada") } returns "conv-1"
            val model = viewModel()
            model.onPhoneNumberChanged("+15551234567")
            model.search()
            var opened: String? = null

            model.startChat { opened = it }

            assertEquals("conv-1", opened)
            coVerify(exactly = 1) { messageRepository.ensureConversation("user-ada") }
        }

    @Test
    fun `a conversation that cannot be opened is reported instead of navigating nowhere`() =
        runTest(dispatcher) {
            coEvery { contactRepository.lookupByPhone(any()) } returns ada
            coEvery { messageRepository.ensureConversation(any()) } throws IllegalStateException("offline")
            val model = viewModel()
            model.onPhoneNumberChanged("+15551234567")
            model.search()
            var opened: String? = null

            model.startChat { opened = it }

            assertEquals(null, opened)
            assertIs<LookupResult.Failed>(model.uiState.value.result)
        }
}
