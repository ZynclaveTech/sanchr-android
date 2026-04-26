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
import com.sanchr.proto.auth.RequestOtpRequest
import com.sanchr.proto.auth.VerifyOTPRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the onboarding state machine defined in [AuthState]:
 *
 * ```
 * Splash -> LoginPhone -> OtpEntry -> Registering -> Done(isNewUser)
 * ```
 *
 * iOS-parity: `Splash -> LoginView` is the canonical path (SanchrApp.swift
 * 350-396). Android lands directly on [AuthState.LoginPhone] after the splash.
 * There is no separate Sign-up entry point — backend's
 * `handle_request_otp` (handlers.rs, commit `bdfb5a7`) handles new vs.
 * returning phones transparently, exactly as iOS does.
 *
 * [attemptFastLogin] only short-circuits when a still-valid access token is
 * already on disk; otherwise the user is routed through the normal phone +
 * OTP flow. There is no cached-password Login-RPC fast path on Android,
 * matching iOS `LoginView` which has no equivalent silent-Login capability.
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
         * Phone-only entry path. Dispatches `AuthService.RequestOtp(phone, device)`
         * — backend handler `handle_request_otp` (handlers.rs, commit `bdfb5a7`)
         * handles both branches transparently: a verified phone short-circuits
         * to OTP re-issue for login; a brand-new phone creates/refreshes a
         * pending registration. Display name is collected post-OTP via
         * `OnboardingNameStepView`.
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
                        authServiceClient.requestOtp(
                            RequestOtpRequest(
                                phoneNumber = phoneE164,
                                device = buildDeviceInfo(),
                            ),
                        )
                    }
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

        // region ── OtpEntry ──────────────────────────────────────────────────

        fun onOtpChanged(otp: String) {
            val current = _state.value as? AuthState.OtpEntry ?: return
            _state.value = current.copy(otp = otp.filter { it.isDigit() }.take(OTP_LENGTH))
        }

        /**
         * Re-request OTP from the backend using the cached phone. RequestOtp
         * is idempotent on `pending_registrations` so calling it again simply
         * refreshes the code.
         */
        fun resendOtp() {
            val current = _state.value as? AuthState.OtpEntry ?: return
            _state.value = current.copy(otp = "", isSubmitting = true)
            viewModelScope.launch {
                try {
                    withContext(dispatchers.io) {
                        authServiceClient.requestOtp(
                            RequestOtpRequest(
                                phoneNumber = current.phoneE164,
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

                    // Server displayName is the source of truth: a blank/missing
                    // value identifies a brand-new account that still needs
                    // profile setup; a populated value identifies a returning
                    // user (existing-phone short-circuit on the backend).
                    val serverDisplayName = response.user?.displayName.orEmpty()
                    val isNewUser = serverDisplayName.isBlank()
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
                        isNewUser = isNewUser,
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
            isNewUser: Boolean,
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

                _state.value = AuthState.Done(isNewUser = isNewUser)
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
         * first composition, racing the splash-duration delay.
         *
         * Single supported path: a still-valid access token is already on disk
         * → jump straight to [AuthState.Done]; the NavHost routes the user out
         * before the splash delay elapses. Otherwise stay on
         * [AuthState.Splash] and let [onSplashComplete] advance to
         * [AuthState.LoginPhone] for the normal phone + OTP flow.
         *
         * There is no cached-password Login-RPC fall-back: post-H2 nothing in
         * the auth feature persists an account password, and iOS `LoginView`
         * has no equivalent silent-Login path either, so this matches iOS
         * behaviour.
         *
         * Emits `Done(isNewUser = false)` on the early-out — fast-login is by
         * definition a returning-user path.
         *
         * @return always `null`; no RPC is launched. Returning [Job]? keeps
         * the call-site signature stable for [SplashScreen]'s race-with-delay
         * pattern in case future paths re-introduce an async branch.
         */
        fun attemptFastLogin(): Job? {
            if (sessionManager.getAccessToken() != null && !sessionManager.isTokenExpired()) {
                _state.value = AuthState.Done(isNewUser = false)
            }
            return null
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

        private fun isValidCountryCode(countryCode: String): Boolean = Regex("^\\+\\d{1,3}$").matches(countryCode)

        private fun isValidSubscriber(phone: String): Boolean =
            phone.all { it.isDigit() } && phone.length in MIN_SUBSCRIBER_DIGITS..MAX_SUBSCRIBER_DIGITS

        // endregion

        private companion object {
            const val OTP_LENGTH = 6
            const val MIN_SUBSCRIBER_DIGITS = 7
            const val MAX_SUBSCRIBER_DIGITS = 15
        }
    }
