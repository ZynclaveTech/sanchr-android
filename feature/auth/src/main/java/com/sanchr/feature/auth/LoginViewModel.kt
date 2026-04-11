package com.sanchr.feature.auth

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.notifications.PushTokenManager
import com.sanchr.proto.auth.AuthServiceClient
import com.sanchr.proto.auth.DeviceInfo
import com.sanchr.proto.auth.RegisterRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sealed UI state hierarchy for the login screen.
 */
sealed interface LoginUiState {
    data object Idle : LoginUiState
    data class PhoneInput(
        val phoneNumber: String = "",
        val countryCode: String = "+1",
        val isValid: Boolean = false,
    ) : LoginUiState

    data object Loading : LoginUiState

    data class OtpSent(
        val fullPhoneNumber: String,
    ) : LoginUiState

    data class Error(
        val message: String,
        val phoneNumber: String = "",
        val countryCode: String = "+1",
    ) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authServiceClient: AuthServiceClient,
    private val sessionManager: SessionManager,
    private val pushTokenManager: PushTokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.PhoneInput())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /**
     * Validates phone number against E.164 format: optional +, country code 1-3 digits,
     * then subscriber number for 7-14 total digits.
     */
    private fun isValidE164(countryCode: String, number: String): Boolean {
        val full = "$countryCode$number"
        // E.164: +[1-9][0-9]{6,14} => 7 to 15 digits total after +
        val digitsOnly = full.removePrefix("+")
        return digitsOnly.length in 7..15 &&
            digitsOnly.all { it.isDigit() } &&
            digitsOnly.first() != '0'
    }

    fun onPhoneNumberChanged(phoneNumber: String) {
        val digitsOnly = phoneNumber.filter { it.isDigit() }
        val currentState = _uiState.value
        val countryCode = when (currentState) {
            is LoginUiState.PhoneInput -> currentState.countryCode
            is LoginUiState.Error -> currentState.countryCode
            else -> "+1"
        }
        _uiState.value = LoginUiState.PhoneInput(
            phoneNumber = digitsOnly,
            countryCode = countryCode,
            isValid = isValidE164(countryCode, digitsOnly),
        )
    }

    fun onCountryCodeChanged(countryCode: String) {
        val currentState = _uiState.value
        val phoneNumber = when (currentState) {
            is LoginUiState.PhoneInput -> currentState.phoneNumber
            is LoginUiState.Error -> currentState.phoneNumber
            else -> ""
        }
        _uiState.value = LoginUiState.PhoneInput(
            phoneNumber = phoneNumber,
            countryCode = countryCode,
            isValid = isValidE164(countryCode, phoneNumber),
        )
    }

    fun requestOtp(onOtpSent: (String) -> Unit) {
        val currentState = _uiState.value
        val (phoneNumber, countryCode) = when (currentState) {
            is LoginUiState.PhoneInput -> currentState.phoneNumber to currentState.countryCode
            is LoginUiState.Error -> currentState.phoneNumber to currentState.countryCode
            else -> return
        }

        if (!isValidE164(countryCode, phoneNumber)) {
            _uiState.value = LoginUiState.Error(
                message = "Please enter a valid phone number",
                phoneNumber = phoneNumber,
                countryCode = countryCode,
            )
            return
        }

        val fullNumber = "$countryCode$phoneNumber"

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val deviceId = sessionManager.getDeviceId() ?: ""
                val request = RegisterRequest(
                    phoneNumber = fullNumber,
                    device = DeviceInfo(
                        deviceName = Build.MODEL ?: deviceId.ifEmpty { "Android" },
                        platform = "android",
                        installationId = sessionManager.getOrCreateInstallationId(),
                        supportsDeliveryAck = true,
                    ),
                )
                val response = authServiceClient.register(request)

                // Store tokens if returned with the register call
                if (response.accessToken.isNotEmpty()) {
                    sessionManager.saveSession(
                        accessToken = response.accessToken,
                        refreshToken = response.refreshToken,
                        userId = response.user?.id ?: "",
                        expiresAtMillis = System.currentTimeMillis() + (response.expiresIn * 1000),
                    )
                    if (response.deviceId > 0) {
                        sessionManager.saveDeviceId(response.deviceId.toString())
                    }

                    // Upload FCM token to backend now that we have a valid session
                    uploadPushToken()
                }

                _uiState.value = LoginUiState.OtpSent(fullPhoneNumber = fullNumber)
                onOtpSent(fullNumber)
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(
                    message = e.message ?: "Failed to send verification code",
                    phoneNumber = phoneNumber,
                    countryCode = countryCode,
                )
            }
        }
    }

    /**
     * Uploads the current FCM token to the backend after successful authentication.
     * Runs in a fire-and-forget fashion; failure is non-fatal.
     */
    private fun uploadPushToken() {
        viewModelScope.launch {
            try {
                pushTokenManager.uploadToken()
            } catch (_: Exception) {
                // Non-critical: token will be retried on next app launch or token rotation
            }
        }
    }

    fun clearError() {
        val currentState = _uiState.value
        if (currentState is LoginUiState.Error) {
            _uiState.value = LoginUiState.PhoneInput(
                phoneNumber = currentState.phoneNumber,
                countryCode = currentState.countryCode,
                isValid = isValidE164(currentState.countryCode, currentState.phoneNumber),
            )
        }
    }
}
