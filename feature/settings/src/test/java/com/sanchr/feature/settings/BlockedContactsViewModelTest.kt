package com.sanchr.feature.settings

import com.sanchr.domain.contacts.BlockedContact
import com.sanchr.domain.contacts.ContactRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class BlockedContactsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<ContactRepository>(relaxed = true)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads the list and unblocking removes the row after the repository call`() =
        runTest(dispatcher) {
            coEvery { repository.blockedContacts() } returns listOf(BlockedContact("u1", "Ada"), BlockedContact("u2", "Grace"))
            val vm = BlockedContactsViewModel(repository)
            advanceUntilIdle()
            assertEquals(
                listOf("Ada", "Grace"),
                vm.uiState.value.blocked
                    .map { it.displayName },
            )

            vm.unblock("u1")
            advanceUntilIdle()
            coVerify { repository.setBlocked("u1", false) }
            assertEquals(
                listOf("Grace"),
                vm.uiState.value.blocked
                    .map { it.displayName },
            )
        }

    @Test
    fun `a load failure is surfaced and cleared`() =
        runTest(dispatcher) {
            coEvery { repository.blockedContacts() } throws IOException("offline")
            val vm = BlockedContactsViewModel(repository)
            advanceUntilIdle()
            assertEquals("offline", vm.uiState.value.error)
            assertEquals(false, vm.uiState.value.isLoading)
            vm.dismissError()
            assertEquals(null, vm.uiState.value.error)
        }
}
