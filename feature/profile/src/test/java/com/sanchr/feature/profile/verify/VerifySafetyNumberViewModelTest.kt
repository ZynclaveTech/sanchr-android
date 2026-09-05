package com.sanchr.feature.profile.verify

import androidx.lifecycle.SavedStateHandle
import com.sanchr.core.crypto.verify.SafetyNumber
import com.sanchr.core.crypto.verify.SafetyNumberManager
import com.sanchr.core.crypto.verify.ScanOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class VerifySafetyNumberViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val safetyNumbers = mockk<SafetyNumberManager>(relaxed = true)
    private val handle = SavedStateHandle(mapOf("userId" to "peer-1"))

    private val number =
        SafetyNumber(
            digits = "12345 67890 11111 22222 33333 44444 55555 66666 77777 88888 99999 00000",
            scannablePayload = byteArrayOf(1, 2, 3),
            verifiedAtMillis = null,
        )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = VerifySafetyNumberViewModel(safetyNumbers, handle)

    @Test
    fun `the code loads and splits into twelve groups`() =
        runTest(dispatcher) {
            coEvery { safetyNumbers.safetyNumber("peer-1") } returns number

            val state = viewModel().uiState.value

            assertNull(state.error)
            assertEquals(12, assertNotNull(state.safetyNumber).digitGroups.size)
            assertFalse(state.isVerified)
        }

    @Test
    fun `a failure shows the reason and never a placeholder number`() =
        runTest(dispatcher) {
            coEvery { safetyNumbers.safetyNumber("peer-1") } throws IllegalStateException("no session")

            val state = viewModel().uiState.value

            assertNull(state.safetyNumber, "a screen with no key must show no digits at all")
            assertNotNull(state.error)
        }

    @Test
    fun `a matching scan records the verification`() =
        runTest(dispatcher) {
            coEvery { safetyNumbers.safetyNumber("peer-1") } returns number
            coEvery { safetyNumbers.compare("peer-1", any()) } returns ScanOutcome.Match
            val model = viewModel()

            model.onScanned(byteArrayOf(9))

            coVerify(exactly = 1) { safetyNumbers.markVerified("peer-1", any(), any()) }
            coVerify(exactly = 0) { safetyNumbers.clearVerified(any(), any()) }
            assertEquals(ScanOutcome.Match, model.uiState.value.scanOutcome)
            assertFalse(model.uiState.value.scannerOpen)
        }

    @Test
    fun `a mismatching scan revokes any earlier verification`() =
        runTest(dispatcher) {
            coEvery { safetyNumbers.safetyNumber("peer-1") } returns number
            coEvery { safetyNumbers.compare("peer-1", any()) } returns ScanOutcome.Mismatch
            val model = viewModel()

            model.onScanned(byteArrayOf(9))

            coVerify(exactly = 1) { safetyNumbers.clearVerified("peer-1", any()) }
            coVerify(exactly = 0) { safetyNumbers.markVerified(any(), any(), any()) }
            assertEquals(ScanOutcome.Mismatch, model.uiState.value.scanOutcome)
        }

    @Test
    fun `an unreadable code changes nothing either way`() =
        runTest(dispatcher) {
            coEvery { safetyNumbers.safetyNumber("peer-1") } returns number
            coEvery { safetyNumbers.compare("peer-1", any()) } returns ScanOutcome.Unreadable
            val model = viewModel()

            model.onScanned(byteArrayOf(9))

            coVerify(exactly = 0) { safetyNumbers.markVerified(any(), any(), any()) }
            coVerify(exactly = 0) { safetyNumbers.clearVerified(any(), any()) }
            assertEquals(ScanOutcome.Unreadable, model.uiState.value.scanOutcome)
        }

    @Test
    fun `a verified number reports itself verified`() =
        runTest(dispatcher) {
            coEvery { safetyNumbers.safetyNumber("peer-1") } returns number.copy(verifiedAtMillis = 1_700_000_000_000)

            assertTrue(viewModel().uiState.value.isVerified)
        }
}
