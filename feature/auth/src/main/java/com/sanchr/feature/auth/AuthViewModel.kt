package com.sanchr.feature.auth

import android.os.Build
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
import com.sanchr.proto.auth.RegisterRequest
import com.sanchr.proto.auth.VerifyOTPRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the onboarding state machine defined in [AuthState]:
 *
 * ```
 * Splash -> Home -> (LoginPhone | RegisterPhoneAndName) -> OtpEntry -> ... -> Done   (new, iOS-parity)
 * PhoneEntry -> ProfileEntry -> OtpEntry -> Permissions -> Registering -> Done       (legacy)
 * ```
 *
 * Phase 2 of the realignment has wired the new Home/LoginPhone/RegisterPhoneAndName
 * state transitions and their screens, and flipped the initial state to [AuthState.Splash]
 * so cold launch hits the new flow. The RPC dispatch on `submitLoginPhone()` and
 * `submitRegister()` remains a Phase-3 `TODO` — those calls currently throw
 * `NotImplementedError` intentionally so UI manual-tests can verify the
 * navigation up to "tap Continue" without accidentally contacting the backend.
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
                        android.util.Log.w(
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
        //
        // RPC dispatch from `submitLoginPhone()` and `submitRegister()` lands in
        // Phase 3; the bodies currently throw `NotImplementedError` through the
        // stdlib `TODO(...)` helper. That's intentional — tapping Continue will
        // crash the app loudly so we catch any premature production routing.

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

        fun submitLoginPhone() {
            val current = _state.value as? AuthState.LoginPhone ?: return
            if (!isValidCountryCode(current.countryCode) || !isValidSubscriber(current.phone)) {
                _state.value = AuthState.Error(current, "Please enter a valid phone number")
                return
            }
            // Phase 3 will dispatch `authServiceClient.register(...)` with an empty
            // display name + bootstrap password, mirroring iOS `AuthRepository.requestOTP`.
            // Until then this path intentionally throws so a premature merge can't
            // silently call the backend.
            TODO("Phase-3: call Login RPC or Register sentinel path")
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
            // Phase 3 will dispatch `authServiceClient.register(...)` with a freshly-
            // generated password mirroring iOS `AuthRepository.register`. Throws
            // until then so premature routing to the network is impossible.
            TODO("Phase-3: call Register with generated password")
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
