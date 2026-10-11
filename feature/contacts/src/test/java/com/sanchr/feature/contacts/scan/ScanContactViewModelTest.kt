package com.sanchr.feature.contacts.scan

import com.sanchr.domain.messaging.MessageRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class ScanContactViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<MessageRepository>(relaxed = true)
    private val user = "3f1c9a52-7d0e-4b6a-9c11-2e8f5a7d4b90"
    private val code = "https://sanchr.com/u/$user"

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `scanning someone's code opens the chat with them`() =
        runTest(dispatcher) {
            coEvery { repository.ensureConversation(user) } returns "conv-1"
            val vm = ScanContactViewModel(repository)
            val opened = mutableListOf<String>()
            val collecting = launch { vm.opened.collect(opened::add) }

            vm.onScanned(code)

            assertEquals(listOf("conv-1"), opened)
            collecting.cancel()
        }

    /** The camera reports the same code many times a second. */
    @Test
    fun `a code held in view is acted on once`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<String>()
            coEvery { repository.ensureConversation(user) } coAnswers { gate.await() }
            val vm = ScanContactViewModel(repository)

            repeat(20) { vm.onScanned(code) }
            assertEquals(ScanContactState.Opening, vm.state.value)
            gate.complete("conv-1")

            coVerify(exactly = 1) { repository.ensureConversation(any()) }
        }

    @Test
    fun `some other QR code is said to be that, and nothing is opened`() =
        runTest(dispatcher) {
            val vm = ScanContactViewModel(repository)

            vm.onScanned("https://example.com/menu")

            assertEquals(ScanContactState.Problem(ScanContactViewModel.NOT_A_SANCHR_CODE), vm.state.value)
            coVerify(exactly = 0) { repository.ensureConversation(any()) }
        }

    @Test
    fun `a chat that cannot be opened says so, and scanning again is possible`() =
        runTest(dispatcher) {
            coEvery { repository.ensureConversation(user) } throws IllegalStateException("offline") andThen "conv-1"
            val vm = ScanContactViewModel(repository)
            val opened = mutableListOf<String>()
            val collecting = launch { vm.opened.collect(opened::add) }

            vm.onScanned(code)
            assertEquals(ScanContactState.Problem(ScanContactViewModel.COULD_NOT_OPEN), vm.state.value)
            vm.onScanned(code) // still showing the problem: ignored
            vm.scanAgain()
            vm.onScanned(code)

            assertEquals(listOf("conv-1"), opened)
            collecting.cancel()
        }

    @Test
    fun `refusing the camera is explained`() {
        val vm = ScanContactViewModel(repository)

        vm.onCameraRefused()

        assertEquals(ScanContactState.Problem(ScanContactViewModel.CAMERA_NEEDED), vm.state.value)
    }
}
