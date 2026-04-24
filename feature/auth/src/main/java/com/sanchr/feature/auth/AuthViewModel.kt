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
private const val BOOTSTRAP_PASSWORD = "sanchr-login-otp-bootstrap-v1"

/**
 * Drives the onboarding state machine defined in [AuthState]:
 *
 * ```
 * Splash -> Home -> (LoginPhone | RegisterPhoneAndName) -> OtpEntry -> ... -> Done   (new, iOS-parity)
 * PhoneEntry -> ProfileEntry -> OtpEntry -> Permissions -> Registering -> Done       (legacy)
 * ```
 *
 * Phase 3 of the realignment has wired the RPC dispatch for
 * `submitLoginPhone()` and `submitRegister()` and added [attemptFastLogin]
 * which restores the session silently using the cached phone + password when
 * we have them, so warm-start returning users skip the Home chooser entirely.
 *
 * Legacy `submitPhone` / `submitProfile` / `onPhoneChanged` / `onDisplayNameChanged`
 * handlers remain live and are covered by `AuthViewModelStateTest`. They will be
 * removed in Phase 4 once the legacy routes are deleted from the NavHost.
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
        // Initial state is [AuthState.Splash] so cold launch enters the new
        // Phase-2 flow. Legacy unit tests call `onPhoneChanged(...)` before any
        // assertions, which unconditionally pins state to `PhoneEntry`, so this
        // switch is source-compatible with the existing `AuthViewModelStateTest`.
        private val _state = MutableStateFlow<AuthState>(AuthState.Splash)
        val state: StateFlow<AuthState> = _state.asStateFlow()

        // region ── PhoneEntry (legacy) ───────────────────────────────────────
        fun onPhoneChanged(
            countryCode: String,
            phone: String,
        ) {
            val digitsOnly = phone.filter { it.isDigit() }
            _state.value = AuthState.PhoneEntry(countryCode = countryCode, phone = digitsOnly)
        }

        fun submitPhone() {
            val current = _state.value as? AuthState.PhoneEntry ?: return
            if (!isValidCountryCode(current.countryCode) || !isValidSubscriber(current.phone)) {
                _state.value = AuthState.Error(current, "Please enter a valid phone number")
                return
            }
            _state.value = AuthState.ProfileEntry(phoneE164 = current.countryCode + current.phone)
        }

        // endregion

        // region ── ProfileEntry (legacy) ─────────────────────────────────────
        fun onDisplayNameChanged(name: String) {
            val current = _state.value as? AuthState.ProfileEntry ?: return
            _state.value = current.copy(displayName = name)
        }

        fun submitProfile() {
            val current = _state.value as? AuthState.ProfileEntry ?: return
            val trimmed = current.displayName.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_DISPLAY_NAME_LENGTH) {
                _state.value = AuthState.Error(current, "Display name must be 1-128 characters")
                return
            }

            _state.value = current.copy(displayName = trimmed, isSubmitting = true)
            viewModelScope.launch {
                try {
                    val password = generateAccountPassword()
                    sessionManager.saveAccountPassword(password)

                    // Backend responds with an OTP-pending payload; tokens aren't
                    // issued until VerifyOTP succeeds, so nothing to persist here.
                    withContext(dispatchers.io) {
                        authServiceClient.register(
                            RegisterRequest(
                                phoneNumber = current.phoneE164,
                                displayName = trimmed,
                                password = password,
                                device = buildDeviceInfo(),
                            ),
                        )
                    }
                    _state.value =
                        AuthState.OtpEntry(
                            phoneE164 = current.phoneE164,
                            displayName = trimmed,
                        )
                } catch (e: Exception) {
                    _state.value =
                        AuthState.Error(
                            current.copy(displayName = trimmed, isSubmitting = false),
                            e.message ?: "Failed to request verification code",
                        )
                }
            }
        }

        /**
         * Re-request OTP from the backend using the cached profile data. Register is
         * idempotent on `pending_registrations` so calling it again simply refreshes
         * the code. Cached password is reused so the user sees the same session.
         */
        fun resendOtp() {
            val current = _state.value as? AuthState.OtpEntry ?: return
            _state.value = current.copy(otp = "", isSubmitting = true)
            viewModelScope.launch {
                try {
                    val password =
                        sessionManager.getAccountPassword()
                            ?: generateAccountPassword().also { sessionManager.saveAccountPassword(it) }
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
        // endregion

        // region ── OtpEntry ──────────────────────────────────────────────────
        fun onOtpChanged(otp: String) {
            val current = _state.value as? AuthState.OtpEntry ?: return
            _state.value = current.copy(otp = otp.filter { it.isDigit() }.take(OTP_LENGTH))
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

                    sessionManager.saveSession(
                        accessToken = response.accessToken,
                        refreshToken = response.refreshToken,
                        userId = userId,
                        expiresAtMillis = System.currentTimeMillis() + (response.expiresIn * 1_000L),
                    )
                    sessionManager.saveDeviceId(response.deviceId.toString())

                    val serverDisplayName = response.user?.displayName.orEmpty()
                    val resolvedDisplayName =
                        if (serverDisplayName.isNotBlank() && serverDisplayName != current.displayName) {
                            serverDisplayName
                        } else {
                            current.displayName
                        }
                    sessionManager.saveDisplayName(resolvedDisplayName)

                    _state.value =
                        AuthState.Permissions(
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

        // region ── Permissions / Registering pipeline ───────────────────────

        fun submitPermissions() {
            val permissions = _state.value as? AuthState.Permissions ?: return
            runRegistrationPipeline(permissions)
        }

        private fun runRegistrationPipeline(permissions: AuthState.Permissions) {
            viewModelScope.launch {
                try {
                    _state.value = AuthState.Registering(RegistrationStep.GENERATING_KEYS, permissions)
                    signalKeyManager.generateIdentity()

                    _state.value = AuthState.Registering(RegistrationStep.UPLOADING_KEYS, permissions)
                    signalKeyManager.uploadInitialKeyBundle()

                    _state.value = AuthState.Registering(RegistrationStep.FETCHING_SENDER_CERT, permissions)
                    senderCertificateManager.refresh()

                    _state.value = AuthState.Registering(RegistrationStep.REGISTERING_PUSH, permissions)
                    // Best-effort: FCM token upload must not block registration. If Play
                    // Services are missing or the backend rejects the token we log and
                    // continue; SyncInitializer's periodic refresh will retry later.
                    try {
                        pushTokenManager.uploadToken()
                    } catch (e: Exception) {
                        Log.w(
                            "AuthViewModel",
                            "Push token upload failed; continuing registration",
                            e,
                        )
                    }

                    _state.value = AuthState.Registering(RegistrationStep.PERSISTING, permissions)
                    withContext(dispatchers.io) {
                        identityKeyStore.initializeAccount(
                            userId = permissions.userId,
                            deviceId = permissions.deviceId.toString(),
                            phoneE164 = permissions.phoneE164,
                        )
                    }

                    _state.value = AuthState.Done
                } catch (e: Exception) {
                    _state.value =
                        AuthState.Error(
                            previousState = permissions,
                            message = e.message ?: "Registration failed",
                        )
                }
            }
        }
        // endregion

        // region ── Error recovery ───────────────────────────────────────────
        fun retry() {
            val err = _state.value as? AuthState.Error ?: return
            _state.value = err.previousState
        }
        // endregion

        // region ── New iOS-parity transitions (Splash / Home / Login / Register)

        /** Splash -> Home; no-op if we're already past the splash. */
        fun onSplashComplete() {
            if (_state.value is AuthState.Splash) {
                _state.value = AuthState.Home()
            }
        }

        /** Home -> LoginPhone. Carries over any pre-filled phone for continuity. */
        fun chooseLogin() {
            val home = _state.value as? AuthState.Home ?: return
            _state.value = AuthState.LoginPhone(phone = home.prefilledPhone)
        }

        /** Home -> RegisterPhoneAndName. Carries over any pre-filled phone. */
        fun chooseRegister() {
            val home = _state.value as? AuthState.Home ?: return
            _state.value = AuthState.RegisterPhoneAndName(phone = home.prefilledPhone)
        }

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

        /**
         * Best-effort silent session restore. Called from the Home screen on
         * first composition. Three paths:
         *
         * 1. Valid unexpired access token already on disk → jump straight to
         *    [AuthState.Done]; the NavHost routes the user out.
         * 2. Cached phone + account password → call `Login` (handlers.rs:413-463
         *    returns tokens directly, no OTP round-trip) and persist the
         *    session. On any failure, stay on Home silently — this is a best-
         *    effort path and surfacing an error would be hostile UX.
         * 3. No cached credentials → return null.
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
                        Log.w("AuthViewModel", "Fast-login response missing session data; staying on Home")
                        return@launch
                    }
                    sessionManager.saveSession(
                        accessToken = response.accessToken,
                        refreshToken = response.refreshToken,
                        userId = userId,
                        expiresAtMillis = System.currentTimeMillis() + (response.expiresIn * 1_000L),
                    )
                    sessionManager.saveDeviceId(response.deviceId.toString())
                    val serverDisplayName = response.user?.displayName.orEmpty()
                    if (serverDisplayName.isNotBlank()) {
                        sessionManager.saveDisplayName(serverDisplayName)
                    }
                    _state.value = AuthState.Done
                } catch (e: Exception) {
                    // Silent by design — user sees Home and can continue manually.
                    Log.w("AuthViewModel", "Fast-login failed; falling back to Home", e)
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
