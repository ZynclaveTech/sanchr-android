package com.sanchr.feature.auth

import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.crypto.SignalKeyManager
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.auth.AuthServiceClient
import com.sanchr.proto.auth.DeviceInfo
import com.sanchr.proto.auth.RegisterRequest
import com.sanchr.proto.media.MediaServiceClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val displayName: String = "",
    val bio: String = "",
    val avatarUri: Uri? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authServiceClient: AuthServiceClient,
    private val mediaServiceClient: MediaServiceClient,
    private val sessionManager: SessionManager,
    private val signalKeyManager: SignalKeyManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onDisplayNameChanged(name: String) {
        _uiState.update { it.copy(displayName = name, errorMessage = null) }
    }

    fun onBioChanged(bio: String) {
        _uiState.update { it.copy(bio = bio) }
    }

    fun onAvatarSelected(uri: Uri) {
        _uiState.update { it.copy(avatarUri = uri) }
    }

    fun register(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.displayName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Display name is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // Step 1: Upload avatar if selected
                var avatarUrl = ""
                if (state.avatarUri != null) {
                    // TODO: Read file bytes from URI, upload via MediaServiceClient
                    // val uploadUrl = mediaServiceClient.getUploadUrl(...)
                    // Upload bytes to presigned URL
                    // val confirm = mediaServiceClient.confirmUpload(...)
                    // avatarUrl = confirm.mediaUrl
                }

                // Step 2: Create user profile via gRPC
                val phoneNumber = sessionManager.getUserId() ?: ""
                val request = RegisterRequest(
                    phoneNumber = phoneNumber,
                    displayName = state.displayName,
                    device = DeviceInfo(
                        deviceName = Build.MODEL ?: "Android",
                        platform = "android",
                        installationId = sessionManager.getOrCreateInstallationId(),
                        supportsDeliveryAck = true,
                    ),
                )
                val response = authServiceClient.register(request)

                // Step 3: Store tokens
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
                }

                // Step 4: Generate Signal Protocol identity keys and upload key bundle
                initializeSignalKeys()

                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Registration failed")
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Initializes the Signal Protocol key material after successful registration.
     *
     * This is a critical step that must complete before the user can send or
     * receive encrypted messages. It:
     * 1. Generates the identity key pair (long-lived Curve25519 key pair).
     * 2. Generates the first signed pre-key (medium-term, rotated monthly).
     * 3. Generates 100 one-time pre-keys (consumed during X3DH).
     * 4. Uploads the complete key bundle to the server so other users can
     *    establish encrypted sessions with this device.
     */
    private suspend fun initializeSignalKeys() {
        // Generate identity key pair and registration ID
        signalKeyManager.generateIdentity()

        // Generate signed pre-key + one-time pre-keys and upload to server
        signalKeyManager.uploadInitialKeyBundle()
    }
}
