package com.sanchr.feature.auth

import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.SignalKeyManager
import com.sanchr.core.crypto.sealed.SenderCertificateManager
import com.sanchr.core.crypto.store.SanchrIdentityKeyStore
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.notifications.PushTokenManager
import com.sanchr.proto.auth.AuthServiceClient
import com.sanchr.proto.auth.DeviceInfo
import com.sanchr.proto.auth.LoginRequest
import com.sanchr.proto.auth.RegisterRequest
import com.sanchr.proto.auth.VerifyOTPRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared sentinel password sent on the *existing-phone* branch of Register.
 *
 * Backend ignores this on the existing-phone path — see
 * `backend-oss/crates/sanchr-core/src/auth/handlers.rs:297-305`, where the
 * Register handler short-circuits to "re-issue OTP" for a verified phone and
 * never compares the submitted password against the stored one. Matches the
 * iOS sentinel pattern (AuthRepository.swift:61-86) so both clients hit the
 * same code path.
 */
internal const val BOOTSTRAP_PASSWORD = "sanchr-login-otp-bootstrap-v1"

/**
 * Drives the onboarding state machine defined in [AuthState]:
 *
 * ```
 * Splash -> LoginPhone -> OtpEntry -> Registering -> Done
 *        \-> RegisterPhoneAndName -> OtpEntry ...
 * ```
 *
 * iOS-parity: `Splash -> LoginView` is the canonical path (SanchrApp.swift
 * 350-396). Android lands directly on [AuthState.LoginPhone] after the splash
 * and surfaces "New to Sanchr? Sign up" on that screen via
 * [switchToRegister] for the new-user path.
 *
 * [attemptFastLogin] restores the session silently using the cached phone +
 * password when we have them, so warm-start returning users skip the phone
 * entry entirely.
 */
@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val authServiceClient: AuthServiceClient,
        private val sessionManager: SessionManager,
        private val dispatchers: DispatcherProvider,
        private val signalKeyManager: SignalKeyManager,
        private val senderCertificateManager: SenderCertificateManager,
        private val pushTokenManager: PushTokenManager,
        private val identityKeyStore: SanchrIdentityKeyStore,
    ) : ViewModel() {
        private val _state = MutableStateFlow<AuthState>(AuthState.Splash)
        val state: StateFlow<AuthState> = _state.asStateFlow()

        // region ── Splash / entry ───────────────────────────────────────────

        /**
         * Splash -> LoginPhone. No-op if [attemptFastLogin] already moved the
         * flow past Splash (e.g. into [AuthState.Done] for a warm-start user).
         * Mirrors iOS `SanchrApp.swift:350-396` where the splash fades into
         * `LoginView` for unauthenticated sessions.
         */
        fun onSplashComplete() {
            if (_state.value is AuthState.Splash) {
                _state.value = AuthState.LoginPhone()
            }
        }

        /**
         * LoginPhone -> RegisterPhoneAndName. Bound to the "New to Sanchr?
         * Sign up" footer affordance on [LoginPhoneScreen]. Carries over any
         * phone digits the user typed so they don't retype after switching.
         */
        fun switchToRegister() {
            val current = _state.value as? AuthState.LoginPhone ?: return
            _state.value =
                AuthState.RegisterPhoneAndName(
                    countryCode = current.countryCode,
                    phone = current.phone,
                )
        }

        /**
         * RegisterPhoneAndName -> LoginPhone. Symmetric to [switchToRegister]
         * so the register screen can offer a "Already have an account? Log in"
         * affordance without duplicating state-transition logic.
         */
        fun switchToLogin() {
            val current = _state.value as? AuthState.RegisterPhoneAndName ?: return
            _state.value =
                AuthState.LoginPhone(
                    countryCode = current.countryCode,
                    phone = current.phone,
                )
        }

        // endregion

        // region ── LoginPhone ────────────────────────────────────────────────

        fun onLoginPhoneChanged(
            countryCode: String,
            phone: String,
        ) {
            val current = _state.value as? AuthState.LoginPhone ?: return
            _state.value =
                current.copy(
                    countryCode = countryCode,
                    phone = phone.filter { it.isDigit() },
                )
        }

        /**
         * Existing-phone login path. Dispatches `Register` with an empty display
         * name + bootstrap password, mirroring iOS `AuthRepository.requestOTP`
         * (AuthRepository.swift:61-86). Backend short-circuits on a verified
         * phone (handlers.rs:297-305) and re-issues an OTP without touching the
         * stored password.
         */
        fun submitLoginPhone() {
            val current = _state.value as? AuthState.LoginPhone ?: return
            if (!isValidCountryCode(current.countryCode) || !isValidSubscriber(current.phone)) {
                _state.value = AuthState.Error(current, "Please enter a valid phone number")
                return
            }
            val phoneE164 = current.countryCode + current.phone
            _state.value = current.copy(isSubmitting = true)
            viewModelScope.launch {
                try {
                    // Persist the phone so resend + future fast-login have it.
                    sessionManager.saveStoredPhoneE164(phoneE164)
                    withContext(dispatchers.io) {
                        authServiceClient.register(
                            RegisterRequest(
                                phoneNumber = phoneE164,
                                displayName = "",
                                password = BOOTSTRAP_PASSWORD,
                                device = buildDeviceInfo(),
                            ),
                        )
                    }
                    // Empty displayName signals the login path to downstream screens.
                    _state.value =
                        AuthState.OtpEntry(
                            phoneE164 = phoneE164,
                            displayName = "",
                        )
                } catch (e: Exception) {
                    _state.value =
                        AuthState.Error(
                            current.copy(isSubmitting = false),
                            e.message ?: "Failed to request verification code",
                        )
                }
            }
        }

        // endregion

        // region ── RegisterPhoneAndName ─────────────────────────────────────

        fun onRegisterChanged(
            countryCode: String,
            phone: String,
            displayName: String,
        ) {
            val current = _state.value as? AuthState.RegisterPhoneAndName ?: return
            _state.value =
                current.copy(
                    countryCode = countryCode,
                    phone = phone.filter { it.isDigit() },
                    displayName = displayName.take(MAX_DISPLAY_NAME_LENGTH),
                )
        }

        /**
         * New-user registration path. Generates a per-account password, persists
         * it, and calls Register with the submitted display name. Mirrors iOS
         * `AuthRepository.register`.
         */
        fun submitRegister() {
            val current = _state.value as? AuthState.RegisterPhoneAndName ?: return
            val trimmedName = current.displayName.trim()
            if (trimmedName.isEmpty() || trimmedName.length > MAX_DISPLAY_NAME_LENGTH) {
                _state.value = AuthState.Error(current, "Display name must be 1-128 characters")
                return
            }
            if (!isValidCountryCode(current.countryCode) || !isValidSubscriber(current.phone)) {
                _state.value = AuthState.Error(current, "Please enter a valid phone number")
                return
            }
            val phoneE164 = current.countryCode + current.phone
            _state.value = current.copy(displayName = trimmedName, isSubmitting = true)
            viewModelScope.launch {
                try {
                    val password = generateAccountPassword()
                    sessionManager.saveAccountPassword(password)
                    sessionManager.saveStoredPhoneE164(phoneE164)
                    withContext(dispatchers.io) {
                        authServiceClient.register(
                            RegisterRequest(
                                phoneNumber = phoneE164,
                                displayName = trimmedName,
                                password = password,
                                device = buildDeviceInfo(),
                            ),
                        )
                    }
                    _state.value =
                        AuthState.OtpEntry(
                            phoneE164 = phoneE164,
                            displayName = trimmedName,
                        )
                } catch (e: Exception) {
                    _state.value =
                        AuthState.Error(
                            current.copy(displayName = trimmedName, isSubmitting = false),
                            e.message ?: "Failed to request verification code",
                        )
                }
            }
        }

        // endregion

        // region ── OtpEntry ──────────────────────────────────────────────────

        fun onOtpChanged(otp: String) {
            val current = _state.value as? AuthState.OtpEntry ?: return
            _state.value = current.copy(otp = otp.filter { it.isDigit() }.take(OTP_LENGTH))
        }

        /**
         * Re-request OTP from the backend using the cached session data. Register
         * is idempotent on `pending_registrations` so calling it again simply
         * refreshes the code.
         */
        fun resendOtp() {
            val current = _state.value as? AuthState.OtpEntry ?: return
            _state.value = current.copy(otp = "", isSubmitting = true)
            viewModelScope.launch {
                try {
                    // Login path: empty displayName uses the bootstrap sentinel so
                    // the backend short-circuits without mutating the stored pw.
                    val password =
                        if (current.displayName.isEmpty()) {
                            BOOTSTRAP_PASSWORD
                        } else {
                            sessionManager.getAccountPassword()
                                ?: generateAccountPassword().also { sessionManager.saveAccountPassword(it) }
                        }
                    withContext(dispatchers.io) {
                        authServiceClient.register(
                            RegisterRequest(
                                phoneNumber = current.phoneE164,
                                displayName = current.displayName,
                                password = password,
                                device = buildDeviceInfo(),
                            ),
                        )
                    }
                    _state.value = current.copy(otp = "", isSubmitting = false)
                } catch (e: Exception) {
                    _state.value =
                        AuthState.Error(
                            current.copy(isSubmitting = false),
                            e.message ?: "Failed to resend verification code",
                        )
                }
            }
        }

        fun submitOtp() {
            val current = _state.value as? AuthState.OtpEntry ?: return
            if (current.otp.length != OTP_LENGTH) {
                _state.value = AuthState.Error(current, "Enter the 6-digit code")
                return
            }

            _state.value = current.copy(isSubmitting = true)
            viewModelScope.launch {
                try {
                    val response =
                        withContext(dispatchers.io) {
                            authServiceClient.verifyOtp(
                                VerifyOTPRequest(
                                    phoneNumber = current.phoneE164,
                                    otpCode = current.otp,
                                    device = buildDeviceInfo(),
                                ),
                            )
                        }

                    val userId = response.user?.id.orEmpty()
                    if (response.accessToken.isEmpty() || userId.isEmpty() || response.deviceId <= 0) {
                        _state.value =
                            AuthState.Error(
                                current.copy(isSubmitting = false),
                                "Verification response missing session data",
                            )
                        return@launch
                    }

                    val serverDisplayName = response.user?.displayName.orEmpty()
                    val resolvedDisplayName =
                        if (serverDisplayName.isNotBlank() && serverDisplayName != current.displayName) {
                            serverDisplayName
                        } else {
                            current.displayName
                        }

                    // Order matters: displayName must land in EncryptedSharedPreferences
                    // before saveSession flips _isAuthenticated, because
                    // AppBootstrapViewModel.hasCompletedOnboarding reads getDisplayName()
                    // in its combine(...) on the isAuthenticated trigger. If we flip
                    // auth first, the combine re-reads a stale null and emits false
                    // for returning users who already have a server-side display name,
                    // silently dropping them back into onboarding. Review finding
                    // P1#1 (Phase 8).
                    sessionManager.saveDisplayName(resolvedDisplayName)
                    sessionManager.saveDeviceId(response.deviceId.toString())
                    sessionManager.saveSession(
                        accessToken = response.accessToken,
                        refreshToken = response.refreshToken,
                        userId = userId,
                        expiresAtMillis = System.currentTimeMillis() + (response.expiresIn * 1_000L),
                    )

                    runRegistrationPipeline(
                        phoneE164 = current.phoneE164,
                        displayName = resolvedDisplayName,
                        userId = userId,
                        deviceId = response.deviceId,
                    )
                } catch (e: Exception) {
                    _state.value =
                        AuthState.Error(
                            current.copy(isSubmitting = false),
                            e.message ?: "Failed to verify code",
                        )
                }
            }
        }

        // endregion

        // region ── Registering pipeline ──────────────────────────────────────

        private suspend fun runRegistrationPipeline(
            phoneE164: String,
            displayName: String,
            userId: String,
            deviceId: Int,
        ) {
            fun stage(step: RegistrationStep) =
                AuthState.Registering(
                    step = step,
                    phoneE164 = phoneE164,
                    displayName = displayName,
                    userId = userId,
                    deviceId = deviceId,
                )
            try {
                _state.value = stage(RegistrationStep.GENERATING_KEYS)
                signalKeyManager.generateIdentity()

                _state.value = stage(RegistrationStep.UPLOADING_KEYS)
                signalKeyManager.uploadInitialKeyBundle()

                _state.value = stage(RegistrationStep.FETCHING_SENDER_CERT)
                senderCertificateManager.refresh()

                _state.value = stage(RegistrationStep.REGISTERING_PUSH)
                // Best-effort: FCM token upload must not block registration. If
                // Play Services are missing or the backend rejects the token we
                // log and continue; SyncInitializer's periodic refresh retries later.
                try {
                    pushTokenManager.uploadToken()
                } catch (e: Exception) {
                    Log.w(
                        "AuthViewModel",
                        "Push token upload failed; continuing registration",
                        e,
                    )
                }

                _state.value = stage(RegistrationStep.PERSISTING)
                withContext(dispatchers.io) {
                    identityKeyStore.initializeAccount(
                        userId = userId,
                        deviceId = deviceId.toString(),
                        phoneE164 = phoneE164,
                    )
                }

                _state.value = AuthState.Done
            } catch (e: Exception) {
                _state.value =
                    AuthState.Error(
                        previousState = stage(RegistrationStep.GENERATING_KEYS),
                        message = e.message ?: "Registration failed",
                    )
            }
        }

        // endregion

        // region ── Error recovery ───────────────────────────────────────────

        fun retry() {
            val err = _state.value as? AuthState.Error ?: return
            _state.value = err.previousState
        }

        // endregion

        // region ── Fast-login ───────────────────────────────────────────────

        /**
         * Best-effort silent session restore. Called from [SplashScreen] on
         * first composition, racing the splash-duration delay. Three paths:
         *
         * 1. Valid unexpired access token already on disk → jump straight to
         *    [AuthState.Done]; the NavHost routes the user out before the
         *    splash delay elapses.
         * 2. Cached phone + account password → call `Login` (handlers.rs:413-463
         *    returns tokens directly, no OTP round-trip) and persist the
         *    session. On any failure, stay on [AuthState.Splash] silently and
         *    let [onSplashComplete] advance to [AuthState.LoginPhone].
         * 3. No cached credentials → return null; [onSplashComplete] handles
         *    the transition to [AuthState.LoginPhone].
         *
         * @return the launched [Job] when an RPC attempt was kicked off, or
         * `null` if no attempt was made (nothing to await).
         */
        fun attemptFastLogin(): Job? {
            if (sessionManager.getAccessToken() != null && !sessionManager.isTokenExpired()) {
                _state.value = AuthState.Done
                return null
            }
            val phoneE164 = sessionManager.getStoredPhoneE164() ?: return null
            val password = sessionManager.getAccountPassword() ?: return null
            return viewModelScope.launch {
                try {
                    val response =
                        withContext(dispatchers.io) {
                            authServiceClient.login(
                                LoginRequest(
                                    phoneNumber = phoneE164,
                                    password = password,
                                    device = buildDeviceInfo(),
                                ),
                            )
                        }
                    val userId = response.user?.id.orEmpty()
                    if (response.accessToken.isEmpty() || userId.isEmpty() || response.deviceId <= 0) {
                        Log.w(
                            "AuthViewModel",
                            "Fast-login response missing session data; staying on Splash",
                        )
                        return@launch
                    }
                    // Order matters: see submitOtp for the full rationale. Persist
                    // displayName before saveSession flips _isAuthenticated, so
                    // AppBootstrapViewModel.hasCompletedOnboarding's isAuthenticated-
                    // triggered re-read of getDisplayName() sees the fresh value.
                    val serverDisplayName = response.user?.displayName.orEmpty()
                    if (serverDisplayName.isNotBlank()) {
                        sessionManager.saveDisplayName(serverDisplayName)
                    }
                    sessionManager.saveDeviceId(response.deviceId.toString())
                    sessionManager.saveSession(
                        accessToken = response.accessToken,
                        refreshToken = response.refreshToken,
                        userId = userId,
                        expiresAtMillis = System.currentTimeMillis() + (response.expiresIn * 1_000L),
                    )
                    _state.value = AuthState.Done
                } catch (e: Exception) {
                    // Silent by design — the splash delay will advance the
                    // user to LoginPhone so they can continue manually.
                    Log.w("AuthViewModel", "Fast-login failed; falling back to LoginPhone", e)
                }
            }
        }

        // endregion

        // region ── Helpers ──────────────────────────────────────────────────

        private fun buildDeviceInfo(): DeviceInfo =
            DeviceInfo(
                deviceName = Build.MODEL ?: "Android",
                platform = "android",
                installationId = sessionManager.getOrCreateInstallationId(),
                supportsDeliveryAck = true,
            )

        private fun generateAccountPassword(): String {
            val bytes = ByteArray(PASSWORD_BYTES).also { SecureRandom().nextBytes(it) }
            return Base64.getEncoder().withoutPadding().encodeToString(bytes)
        }

        private fun isValidCountryCode(countryCode: String): Boolean = Regex("^\\+\\d{1,3}$").matches(countryCode)

        private fun isValidSubscriber(phone: String): Boolean =
            phone.all { it.isDigit() } && phone.length in MIN_SUBSCRIBER_DIGITS..MAX_SUBSCRIBER_DIGITS

        // endregion

        private companion object {
            const val MAX_DISPLAY_NAME_LENGTH = 128
            const val PASSWORD_BYTES = 32
            const val OTP_LENGTH = 6
            const val MIN_SUBSCRIBER_DIGITS = 7
            const val MAX_SUBSCRIBER_DIGITS = 15
        }
    }
