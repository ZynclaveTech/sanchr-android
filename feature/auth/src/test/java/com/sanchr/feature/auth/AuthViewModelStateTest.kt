package com.sanchr.feature.auth

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.auth.AuthResponse
import com.sanchr.proto.auth.AuthServiceClient
import com.sanchr.proto.auth.RegisterRequest
import com.sanchr.proto.auth.User
import com.sanchr.proto.auth.VerifyOTPRequest
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelStateTest {
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

    private fun newViewModel(): AuthViewModel {
        every { sessionManager.getOrCreateInstallationId() } returns "install-1"
        return AuthViewModel(
            authServiceClient = authServiceClient,
            sessionManager = sessionManager,
            dispatchers = dispatchers,
        )
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `valid phone transitions to ProfileEntry`() =
        runTest {
            val vm = newViewModel()
            vm.onPhoneChanged("+1", "4155551234")
            vm.submitPhone()
            assertEquals(AuthState.ProfileEntry(phoneE164 = "+14155551234"), vm.state.value)
        }

    @Test
    fun `invalid phone transitions to Error`() =
        runTest {
            val vm = newViewModel()
            vm.onPhoneChanged("+1", "123")
            vm.submitPhone()
            val s = vm.state.value
            assertIs<AuthState.Error>(s)
            assertIs<AuthState.PhoneEntry>(s.previousState)
        }

    @Test
    fun `submitProfile success transitions to OtpEntry and calls register with full payload`() =
        runTest {
            val request = slot<RegisterRequest>()
            coEvery { authServiceClient.register(capture(request)) } returns AuthResponse()
            every { sessionManager.saveAccountPassword(any()) } just Runs

            val vm = newViewModel()
            vm.onPhoneChanged("+1", "4155551234")
            vm.submitPhone()
            vm.onDisplayNameChanged("Alice")
            vm.submitProfile()
            advanceUntilIdle()

            assertEquals(
                AuthState.OtpEntry(phoneE164 = "+14155551234", displayName = "Alice"),
                vm.state.value,
            )
            val captured = request.captured
            assertEquals("+14155551234", captured.phoneNumber)
            assertEquals("Alice", captured.displayName)
            assertTrue(captured.password.isNotEmpty(), "password must be populated")
            assertEquals("install-1", captured.device?.installationId)
            verify { sessionManager.saveAccountPassword(captured.password) }
        }

    @Test
    fun `submitProfile failure transitions to Error with ProfileEntry`() =
        runTest {
            coEvery { authServiceClient.register(any()) } throws RuntimeException("boom")

            val vm = newViewModel()
            vm.onPhoneChanged("+1", "4155551234")
            vm.submitPhone()
            vm.onDisplayNameChanged("Alice")
            vm.submitProfile()
            advanceUntilIdle()

            val s = vm.state.value
            assertIs<AuthState.Error>(s)
            assertIs<AuthState.ProfileEntry>(s.previousState)
            assertEquals("boom", s.message)
        }

    @Test
    fun `submitOtp success transitions to Permissions and persists tokens`() =
        runTest {
            coEvery { authServiceClient.register(any()) } returns AuthResponse()
            coEvery { authServiceClient.verifyOtp(any<VerifyOTPRequest>()) } returns
                AuthResponse(
                    accessToken = "at",
                    refreshToken = "rt",
                    expiresIn = 3600,
                    user = User(id = "user-42", displayName = "Alice"),
                    deviceId = 7,
                )

            val vm = newViewModel()
            vm.onPhoneChanged("+1", "4155551234")
            vm.submitPhone()
            vm.onDisplayNameChanged("Alice")
            vm.submitProfile()
            advanceUntilIdle()
            vm.onOtpChanged("123456")
            vm.submitOtp()
            advanceUntilIdle()

            val s = vm.state.value
            assertIs<AuthState.Permissions>(s)
            assertEquals("user-42", s.userId)
            assertEquals(7, s.deviceId)
            assertEquals("+14155551234", s.phoneE164)
            assertEquals("Alice", s.displayName)
            verify {
                sessionManager.saveSession(
                    accessToken = "at",
                    refreshToken = "rt",
                    userId = "user-42",
                    expiresAtMillis = any(),
                )
                sessionManager.saveDeviceId("7")
                sessionManager.saveDisplayName("Alice")
            }
        }

    @Test
    fun `retry from Error restores previous state`() =
        runTest {
            val vm = newViewModel()
            vm.onPhoneChanged("+1", "123")
            vm.submitPhone()
            assertIs<AuthState.Error>(vm.state.value)
            vm.retry()
            assertEquals(AuthState.PhoneEntry(countryCode = "+1", phone = "123"), vm.state.value)
        }
}
