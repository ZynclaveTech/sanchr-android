package com.sanchr.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val phoneNumber: String = "",
    val countryCode: String = "+1",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    // TODO: Inject AuthRepository for sending OTP
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onPhoneNumberChanged(phoneNumber: String) {
        _uiState.update {
            it.copy(
                phoneNumber = phoneNumber.filter { ch -> ch.isDigit() },
                errorMessage = null,
            )
        }
    }

    fun onCountryCodeChanged(countryCode: String) {
        _uiState.update { it.copy(countryCode = countryCode) }
    }

    fun requestOtp(onSuccess: (String) -> Unit) {
        val state = _uiState.value
        if (state.phoneNumber.length < 7) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid phone number") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val fullNumber = "${state.countryCode}${state.phoneNumber}"
                // TODO: Call auth repository to request OTP via gRPC
                onSuccess(fullNumber)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to send OTP") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
