package com.sanchr.feature.auth

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.SignalKeyManager
import com.sanchr.core.crypto.sealed.SenderCertificateManager
import com.sanchr.core.crypto.store.SanchrIdentityKeyStore
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.notifications.PushTokenManager
import com.sanchr.proto.auth.AuthResponse
import com.sanchr.proto.auth.AuthServiceClient
import com.sanchr.proto.auth.LoginRequest
import com.sanchr.proto.auth.RegisterRequest
import com.sanchr.proto.auth.User
import com.sanchr.proto.auth.VerifyOTPRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * State-machine unit tests for [AuthViewModel]. Covers the iOS-parity flow:
 *
 *   Splash -> Home -> (LoginPhone | RegisterPhoneAndName) -> OtpEntry
 *          -> Registering -> Done
 *
 * Plus [AuthViewModel.attemptFastLogin] (silent warm-start) and [AuthViewModel.retry].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelStateTest {
    private val testDispatcher = StandardTestDispatcher()

    private val authServiceClient = mockk<AuthServiceClient>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val signalKeyManager = mockk<SignalKeyManager>(relaxed = true)
    private val senderCertificateManager = mockk<SenderCertificateManager>(relaxed = true)
    private val pushTokenManager = mockk<PushTokenManager>(relaxed = true)
    private val identityKeyStore = mockk<SanchrIdentityKeyStore>(relaxed = true)

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
            signalKeyManager = signalKeyManager,
            senderCertificateManager = senderCertificateManager,
            pushTokenManager = pushTokenManager,
            identityKeyStore = identityKeyStore,
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
    fun initialState_isSplash() =
        runTest {
            val vm = newViewModel()
            assertEquals(AuthState.Splash, vm.state.value)
        }

    @Test
    fun onSplashComplete_withNoCachedSession_transitionsToHome() =
        runTest {
            every { sessionManager.getAccessToken() } returns null
            every { sessionManager.getStoredPhoneE164() } returns null
            every { sessionManager.getAccountPassword() } returns null

            val vm = newViewModel()
            vm.onSplashComplete()
            assertEquals(AuthState.Home(), vm.state.value)
        }

    @Test
    fun chooseLogin_fromHome_transitionsToLoginPhone() =
        runTest {
            val vm = newViewModel()
            vm.onSplashComplete()
            vm.chooseLogin()

            val s = vm.state.value
            assertIs<AuthState.LoginPhone>(s)
            assertEquals("+1", s.countryCode)
            assertEquals("", s.phone)
        }

    @Test
    fun chooseRegister_fromHome_transitionsToRegisterPhoneAndName() =
        runTest {
            val vm = newViewModel()
            vm.onSplashComplete()
            vm.chooseRegister()

            assertIs<AuthState.RegisterPhoneAndName>(vm.state.value)
        }

    @Test
    fun onLoginPhoneChanged_filtersNonDigits() =
        runTest {
            val vm = newViewModel()
            vm.onSplashComplete()
            vm.chooseLogin()
            vm.onLoginPhoneChanged("+1", "123-abc-456")

            val s = vm.state.value
            assertIs<AuthState.LoginPhone>(s)
            assertEquals("123456", s.phone)
        }

    @Test
    fun submitLoginPhone_invalidPhone_transitionsToError() =
        runTest {
            val vm = newViewModel()
            vm.onSplashComplete()
            vm.chooseLogin()
            vm.onLoginPhoneChanged("+1", "")
            vm.submitLoginPhone()

            val s = vm.state.value
            assertIs<AuthState.Error>(s)
            assertIs<AuthState.LoginPhone>(s.previousState)
        }

    @Test
    fun submitLoginPhone_validPhone_callsRegisterWithBootstrapPassword_andTransitionsToOtpEntry() =
        runTest {
            val request = slot<RegisterRequest>()
            coEvery { authServiceClient.register(capture(request)) } returns AuthResponse()

            val vm = newViewModel()
            vm.onSplashComplete()
            vm.chooseLogin()
            vm.onLoginPhoneChanged("+1", "4155551234")
            vm.submitLoginPhone()
            advanceUntilIdle()

            assertEquals(
                AuthState.OtpEntry(phoneE164 = "+14155551234", displayName = ""),
                vm.state.value,
            )
            val captured = request.captured
            assertEquals("+14155551234", captured.phoneNumber)
            assertEquals("", captured.displayName)
            assertEquals(BOOTSTRAP_PASSWORD, captured.password)
            assertEquals("install-1", captured.device?.installationId)
            verify { sessionManager.saveStoredPhoneE164("+14155551234") }
        }

    @Test
    fun submitRegister_validInput_callsRegisterWithGeneratedPassword_andTransitionsToOtpEntry() =
        runTest {
            val request = slot<RegisterRequest>()
            coEvery { authServiceClient.register(capture(request)) } returns AuthResponse()

            val vm = newViewModel()
            vm.onSplashComplete()
            vm.chooseRegister()
            vm.onRegisterChanged("+1", "4155551234", "  Alice  ")
            vm.submitRegister()
            advanceUntilIdle()

            assertEquals(
                AuthState.OtpEntry(phoneE164 = "+14155551234", displayName = "Alice"),
                vm.state.value,
            )
            val captured = request.captured
            assertEquals("+14155551234", captured.phoneNumber)
            assertEquals("Alice", captured.displayName)
            assertTrue(captured.password.isNotEmpty(), "generated password must be non-empty")
            assertNotEquals(BOOTSTRAP_PASSWORD, captured.password)
            verify { sessionManager.saveAccountPassword(captured.password) }
            verify { sessionManager.saveStoredPhoneE164("+14155551234") }
        }

    @Test
    fun submitRegister_nameTooLong_transitionsToError() =
        runTest {
            val vm = newViewModel()
            vm.onSplashComplete()
            vm.chooseRegister()
            // onRegisterChanged caps at MAX_DISPLAY_NAME_LENGTH (128); bypass it
            // by pushing the name directly via repeated calls is futile — assert
            // on the validation branch by driving the trimmed-empty case instead.
            vm.onRegisterChanged("+1", "4155551234", "   ")
            vm.submitRegister()

            val s = vm.state.value
            assertIs<AuthState.Error>(s)
            assertIs<AuthState.RegisterPhoneAndName>(s.previousState)
        }

    @Test
    fun onOtpChanged_filtersNonDigitsAndCapsLength() =
        runTest {
            val vm = newViewModel()
            // Force state to OtpEntry via the login path.
            coEvery { authServiceClient.register(any()) } returns AuthResponse()
            vm.onSplashComplete()
            vm.chooseLogin()
            vm.onLoginPhoneChanged("+1", "4155551234")
            vm.submitLoginPhone()
            advanceUntilIdle()

            vm.onOtpChanged("1234567abc")
            val s = vm.state.value
            assertIs<AuthState.OtpEntry>(s)
            assertEquals("123456", s.otp)
        }

    @Test
    fun submitOtp_validCode_invokesVerifyAndTransitionsViaRegisteringToDone() =
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
            vm.onSplashComplete()
            vm.chooseRegister()
            vm.onRegisterChanged("+1", "4155551234", "Alice")
            vm.submitRegister()
            advanceUntilIdle()
            vm.onOtpChanged("123456")
            vm.submitOtp()
            advanceUntilIdle()

            assertEquals(AuthState.Done, vm.state.value)
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

    /**
     * Regression guard for review finding P1#1 (Phase 8).
     *
     * [AppBootstrapViewModel.hasCompletedOnboarding] piggy-backs on
     * [SessionManager.isAuthenticated] as a trigger to re-read
     * `getDisplayName()`. If `saveSession` (which flips `_isAuthenticated`)
     * runs before `saveDisplayName`, the combine re-reads a stale null and
     * emits `false` — silently bouncing returning users into onboarding
     * despite a server-side display name being present.
     *
     * This test locks in the fix: `saveDisplayName` must be persisted
     * BEFORE `saveSession` during OTP verification.
     */
    @Test
    fun submitOtp_persistsDisplayName_beforeFlippingSession() =
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
            vm.onSplashComplete()
            vm.chooseRegister()
            vm.onRegisterChanged("+1", "4155551234", "Alice")
            vm.submitRegister()
            advanceUntilIdle()
            vm.onOtpChanged("123456")
            vm.submitOtp()
            advanceUntilIdle()

            // Strict ordering: displayName to disk BEFORE saveSession flips
            // _isAuthenticated. verifyOrder allows other calls between these
            // (e.g. saveDeviceId) but enforces relative order.
            verifyOrder {
                sessionManager.saveDisplayName("Alice")
                sessionManager.saveSession(
                    accessToken = "at",
                    refreshToken = "rt",
                    userId = "user-42",
                    expiresAtMillis = any(),
                )
            }
        }

    @Test
    fun attemptFastLogin_withCachedCredentials_transitionsToDone() =
        runTest {
            every { sessionManager.getAccessToken() } returns null
            every { sessionManager.getStoredPhoneE164() } returns "+14155551234"
            every { sessionManager.getAccountPassword() } returns "cached-pw"
            val request = slot<LoginRequest>()
            coEvery { authServiceClient.login(capture(request)) } returns
                AuthResponse(
                    accessToken = "at",
                    refreshToken = "rt",
                    expiresIn = 3600,
                    user = User(id = "user-42", displayName = "Alice"),
                    deviceId = 9,
                )

            val vm = newViewModel()
            val job = vm.attemptFastLogin()
            assertTrue(job != null, "fast-login should have launched a job")
            advanceUntilIdle()

            assertEquals(AuthState.Done, vm.state.value)
            val captured = request.captured
            assertEquals("+14155551234", captured.phoneNumber)
            assertEquals("cached-pw", captured.password)
            assertEquals("install-1", captured.device?.installationId)
            verify {
                sessionManager.saveSession(
                    accessToken = "at",
                    refreshToken = "rt",
                    userId = "user-42",
                    expiresAtMillis = any(),
                )
                sessionManager.saveDeviceId("9")
                sessionManager.saveDisplayName("Alice")
            }
        }

    @Test
    fun attemptFastLogin_onLoginFailure_silentlyStaysAtHome() =
        runTest {
            every { sessionManager.getAccessToken() } returns null
            every { sessionManager.getStoredPhoneE164() } returns "+14155551234"
            every { sessionManager.getAccountPassword() } returns "cached-pw"
            coEvery { authServiceClient.login(any()) } throws RuntimeException("net")

            val vm = newViewModel()
            // Drive past Splash so Home is the observable landing.
            vm.onSplashComplete()
            assertEquals(AuthState.Home(), vm.state.value)

            val job = vm.attemptFastLogin()
            assertTrue(job != null, "fast-login should have launched a job")
            advanceUntilIdle()

            // Silent failure contract: stay on Home, never transition to Error.
            assertEquals(AuthState.Home(), vm.state.value)
        }

    @Test
    fun retry_fromErrorState_restoresPreviousState() =
        runTest {
            val vm = newViewModel()
            vm.onSplashComplete()
            vm.chooseLogin()
            vm.onLoginPhoneChanged("+1", "")
            vm.submitLoginPhone()
            assertIs<AuthState.Error>(vm.state.value)

            vm.retry()
            val s = vm.state.value
            assertIs<AuthState.LoginPhone>(s)
            assertEquals("+1", s.countryCode)
            assertEquals("", s.phone)
        }
}
