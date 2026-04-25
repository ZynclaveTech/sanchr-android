package com.sanchr.feature.auth

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.SignalKeyManager
import com.sanchr.core.crypto.sealed.SenderCertificateManager
import com.sanchr.core.crypto.store.SanchrIdentityKeyStore
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.notifications.PushTokenManager
import com.sanchr.proto.auth.AuthResponse
import com.sanchr.proto.auth.AuthServiceClient
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
 * State-machine unit tests for [AuthViewModel]. Covers the iOS-parity flow:
 *
 *   Splash -> LoginPhone -> OtpEntry -> Registering -> Done(isNewUser)
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
    fun onSplashComplete_withNoCachedSession_transitionsToLoginPhone() =
        runTest {
            every { sessionManager.getAccessToken() } returns null
            every { sessionManager.getStoredPhoneE164() } returns null

            val vm = newViewModel()
            vm.onSplashComplete()

            val s = vm.state.value
            assertIs<AuthState.LoginPhone>(s)
            assertEquals("+1", s.countryCode)
            assertEquals("", s.phone)
        }

    @Test
    fun onLoginPhoneChanged_filtersNonDigits() =
        runTest {
            val vm = newViewModel()
            vm.onSplashComplete()
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
    fun onOtpChanged_filtersNonDigitsAndCapsLength() =
        runTest {
            val vm = newViewModel()
            // Force state to OtpEntry via the login path.
            coEvery { authServiceClient.register(any()) } returns AuthResponse()
            vm.onSplashComplete()
            vm.onLoginPhoneChanged("+1", "4155551234")
            vm.submitLoginPhone()
            advanceUntilIdle()

            vm.onOtpChanged("1234567abc")
            val s = vm.state.value
            assertIs<AuthState.OtpEntry>(s)
            assertEquals("123456", s.otp)
        }

    /**
     * Returning user: backend's existing-phone short-circuit (handlers.rs:267-328)
     * yields a populated `displayName` on the verify-OTP response. Surface
     * `Done(isNewUser = false)` so downstream onboarding routing skips the
     * profile-setup detour for someone who already has a profile.
     */
    @Test
    fun submitOtp_returningUser_emitsDoneIsNewUserFalse() =
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
            vm.onLoginPhoneChanged("+1", "4155551234")
            vm.submitLoginPhone()
            advanceUntilIdle()
            vm.onOtpChanged("123456")
            vm.submitOtp()
            advanceUntilIdle()

            val s = vm.state.value
            assertIs<AuthState.Done>(s)
            assertFalse(s.isNewUser, "server-side displayName means returning user")
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
     * New user: backend returns an empty/blank `displayName` on the first
     * verify-OTP because the freshly created account has no profile yet.
     * Surface `Done(isNewUser = true)` so downstream routes the user through
     * profile setup.
     */
    @Test
    fun submitOtp_newUser_emitsDoneIsNewUserTrue() =
        runTest {
            coEvery { authServiceClient.register(any()) } returns AuthResponse()
            coEvery { authServiceClient.verifyOtp(any<VerifyOTPRequest>()) } returns
                AuthResponse(
                    accessToken = "at",
                    refreshToken = "rt",
                    expiresIn = 3600,
                    user = User(id = "user-99", displayName = ""),
                    deviceId = 3,
                )

            val vm = newViewModel()
            vm.onSplashComplete()
            vm.onLoginPhoneChanged("+1", "4155551234")
            vm.submitLoginPhone()
            advanceUntilIdle()
            vm.onOtpChanged("123456")
            vm.submitOtp()
            advanceUntilIdle()

            val s = vm.state.value
            assertIs<AuthState.Done>(s)
            assertTrue(s.isNewUser, "blank server displayName means new user")
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
            vm.onLoginPhoneChanged("+1", "4155551234")
            vm.submitLoginPhone()
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

    /**
     * Path 1 of [AuthViewModel.attemptFastLogin]: a still-valid access token
     * is already on disk, so we short-circuit straight to
     * `Done(isNewUser = false)` without any RPC. This is the only fast-login
     * path post-H2 (the cached-password Login-RPC branch was removed because
     * nothing in the auth feature persists an account password anymore, and
     * iOS `LoginView` has no equivalent silent-Login path either).
     */
    @Test
    fun attemptFastLogin_withValidAccessToken_transitionsToDoneIsNewUserFalse() =
        runTest {
            every { sessionManager.getAccessToken() } returns "live-at"
            every { sessionManager.isTokenExpired() } returns false

            val vm = newViewModel()
            val job = vm.attemptFastLogin()
            advanceUntilIdle()

            assertEquals(null, job, "fast-login no longer launches any RPC")
            val s = vm.state.value
            assertIs<AuthState.Done>(s)
            assertFalse(s.isNewUser, "fast-login is by definition a returning user")
        }

    /**
     * No cached session at all: stay on [AuthState.Splash] silently so the
     * splash-duration delay can advance the user to [AuthState.LoginPhone]
     * via [AuthViewModel.onSplashComplete].
     */
    @Test
    fun attemptFastLogin_withNoCachedSession_staysOnSplash() =
        runTest {
            every { sessionManager.getAccessToken() } returns null
            every { sessionManager.getStoredPhoneE164() } returns null

            val vm = newViewModel()
            assertEquals(AuthState.Splash, vm.state.value)

            val job = vm.attemptFastLogin()
            advanceUntilIdle()

            assertEquals(null, job, "no RPC should have been launched")
            assertEquals(AuthState.Splash, vm.state.value)
        }

    @Test
    fun retry_fromErrorState_restoresPreviousState() =
        runTest {
            val vm = newViewModel()
            vm.onSplashComplete()
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
