package com.sanchr.feature.auth

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.auth.AuthServiceClient
import com.sanchr.proto.auth.RequestOtpResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * An error must not be the end of the screen. The fields are drawn from the
 * state under an error, but used to take input only when none was showing, so
 * after any failure they showed their text and ignored every key.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelAfterErrorTest {
    private val testDispatcher = StandardTestDispatcher()
    private val authServiceClient = mockk<AuthServiceClient>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val dispatchers =
        object : DispatcherProvider {
            override val main = testDispatcher
            override val io = testDispatcher
            override val default = testDispatcher
            override val unconfined = testDispatcher
            override val signalDispatcher = testDispatcher
        }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sessionManager.getOrCreateInstallationId() } returns "install-1"
        coEvery { authServiceClient.requestOtp(any()) } returns RequestOtpResponse(expiresInSeconds = 300, existingUser = false)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        AuthViewModel(
            authServiceClient = authServiceClient,
            sessionManager = sessionManager,
            dispatchers = dispatchers,
            signalKeyManager = mockk(relaxed = true),
            senderCertificateManager = mockk(relaxed = true),
            pushTokenManager = mockk(relaxed = true),
            identityKeyStore = mockk(relaxed = true),
        ).also { it.onSplashComplete() }

    private fun AuthViewModel.atCodeEntry() {
        onLoginPhoneChanged("+91", "9876543210", "IN")
        submitLoginPhone()
        testDispatcher.scheduler.advanceUntilIdle()
        assertIs<AuthState.OtpEntry>(state.value)
    }

    @Test
    fun `a number refused as invalid can still be edited`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            vm.onLoginPhoneChanged("+1", "4155551234567890")
            vm.submitLoginPhone()
            assertIs<AuthState.Error>(vm.state.value)

            vm.onLoginPhoneChanged("+1", "4155551234")

            // Typing answers the error, and takes it down.
            assertEquals("4155551234", assertIs<AuthState.LoginPhone>(vm.state.value).phone)
        }

    @Test
    fun `a refused code is cleared and a new one can be typed`() =
        runTest(testDispatcher) {
            coEvery { authServiceClient.verifyOtp(any()) } throws IllegalStateException("invalid code")
            val vm = viewModel()
            vm.atCodeEntry()
            vm.onOtpChanged("111111")
            vm.submitOtp()
            advanceUntilIdle()
            val refused = assertIs<AuthState.Error>(vm.state.value)
            assertEquals("", assertIs<AuthState.OtpEntry>(refused.previousState).otp)

            vm.onOtpChanged("222222")

            assertEquals("222222", assertIs<AuthState.OtpEntry>(vm.state.value).otp)
        }

    @Test
    fun `a code too short to send is kept, and can be finished`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            vm.atCodeEntry()
            vm.onOtpChanged("123")
            vm.submitOtp()
            assertIs<AuthState.Error>(vm.state.value)

            vm.onOtpChanged("123456")

            assertEquals("123456", assertIs<AuthState.OtpEntry>(vm.state.value).otp)
        }

    @Test
    fun `the registration lock PIN can be typed over an error too`() =
        runTest(testDispatcher) {
            coEvery { authServiceClient.verifyOtp(any()) } throws IllegalStateException("registration_lock_pin_required")
            val vm = viewModel()
            vm.atCodeEntry()
            vm.onOtpChanged("123456")
            vm.submitOtp()
            advanceUntilIdle()
            assertTrue(assertIs<AuthState.OtpEntry>(vm.state.value).pinRequired)
            vm.submitOtp() // no PIN yet
            assertIs<AuthState.Error>(vm.state.value)

            vm.onRegistrationLockPinChanged("4321")

            assertEquals("4321", assertIs<AuthState.OtpEntry>(vm.state.value).pin)
        }

    @Test
    fun `back from the code screen returns to the number that was entered`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            vm.atCodeEntry()

            assertTrue(vm.backToPhoneEntry())

            val phone = assertIs<AuthState.LoginPhone>(vm.state.value)
            assertEquals("+91", phone.countryCode)
            assertEquals("IN", phone.countryIso)
            assertEquals("9876543210", phone.phone)
            assertFalse(phone.isSubmitting)
        }

    @Test
    fun `back works with an error showing over the code screen`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            vm.atCodeEntry()
            vm.submitOtp() // empty code
            assertIs<AuthState.Error>(vm.state.value)

            assertTrue(vm.backToPhoneEntry())

            assertIs<AuthState.LoginPhone>(vm.state.value)
        }

    @Test
    fun `back does nothing from the phone screen, and a new code can be asked for after going back`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            assertFalse(vm.backToPhoneEntry())
            vm.atCodeEntry()
            vm.backToPhoneEntry()

            vm.onLoginPhoneChanged("+91", "9876500000")
            vm.submitLoginPhone()
            advanceUntilIdle()

            assertEquals("+919876500000", assertIs<AuthState.OtpEntry>(vm.state.value).phoneE164)
            coVerify(exactly = 2) { authServiceClient.requestOtp(any()) }
        }

    /** A UK number typed the way it is written went to the server with its 0 still on. */
    @Test
    fun `the number registered leaves off the zero dialled at home`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            vm.onLoginPhoneChanged("+44", "07700900123", "GB")
            vm.submitLoginPhone()
            advanceUntilIdle()

            assertEquals("+447700900123", assertIs<AuthState.OtpEntry>(vm.state.value).phoneE164)
        }

    @Test
    fun `a country outside the old short list can sign in`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            vm.onLoginPhoneChanged("+977", "9841234567", "NP")
            vm.submitLoginPhone()
            advanceUntilIdle()

            assertEquals("+9779841234567", assertIs<AuthState.OtpEntry>(vm.state.value).phoneE164)
        }

    @Test
    fun `a number too short for its country is refused before anything is sent`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            vm.onLoginPhoneChanged("+91", "98765", "IN")
            vm.submitLoginPhone()
            advanceUntilIdle()

            assertIs<AuthState.Error>(vm.state.value)
            coVerify(exactly = 0) { authServiceClient.requestOtp(any()) }
        }
}
