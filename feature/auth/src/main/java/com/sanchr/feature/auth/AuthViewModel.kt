package com.sanchr.feature.auth

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.datastore.SessionManager
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
 * PhoneEntry -> ProfileEntry -> OtpEntry -> Permissions -> Registering -> Done
 * ```
 *
 * This commit wires the pre-pipeline portion (PhoneEntry → ProfileEntry →
 * OtpEntry → Permissions). The post-OTP registration pipeline is added in a
 * follow-up commit.
 *
 * The legacy [LoginViewModel] / [RegisterViewModel] remain in place while Task 5
 * migrates the navigation graph; both may coexist without touching shared state.
 */
@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val authServiceClient: AuthServiceClient,
        private val sessionManager: SessionManager,
        private val dispatchers: DispatcherProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow<AuthState>(AuthState.PhoneEntry())
        val state: StateFlow<AuthState> = _state.asStateFlow()

        // region ── PhoneEntry ────────────────────────────────────────────────
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

        // region ── ProfileEntry ──────────────────────────────────────────────
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
                            current,
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

        fun submitOtp() {
            val current = _state.value as? AuthState.OtpEntry ?: return
            if (current.otp.length != OTP_LENGTH) {
                _state.value = AuthState.Error(current, "Enter the 6-digit code")
                return
            }

            viewModelScope.launch {
                try {
                    val response =
                        withContext(dispatchers.io) {
                            authServiceClient.verifyOtp(
                                VerifyOTPRequest(
                                    phoneNumber = current.phoneE164,
                                    code = current.otp,
                                    device = buildDeviceInfo(),
                                ),
                            )
                        }

                    val userId = response.user?.id.orEmpty()
                    if (response.accessToken.isEmpty() || userId.isEmpty() || response.deviceId <= 0) {
                        _state.value = AuthState.Error(current, "Verification response missing session data")
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
                        AuthState.Error(current, e.message ?: "Failed to verify code")
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
