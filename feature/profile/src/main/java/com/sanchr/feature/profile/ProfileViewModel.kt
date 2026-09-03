package com.sanchr.feature.profile

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.proto.media.GetUploadUrlRequest
import com.sanchr.proto.media.MediaServiceClient
import com.sanchr.proto.settings.GetSettingsRequest
import com.sanchr.proto.settings.SettingsServiceClient
import com.sanchr.proto.settings.UpdateProfileRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val userId: String = "",
    val displayName: String = "",
    val phoneNumber: String = "",
    val avatarUrl: String = "",
    val bio: String = "",
    val isOwnProfile: Boolean = false,
    val isEditing: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val editDisplayName: String = "",
    val editBio: String = "",
    val errorMessage: String? = null,
)

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val settingsServiceClient: SettingsServiceClient,
        private val mediaServiceClient: MediaServiceClient,
    ) : ViewModel() {
        companion object {
            private const val TAG = "ProfileViewModel"
        }

        private val userId: String = checkNotNull(savedStateHandle["userId"])

        private val _uiState = MutableStateFlow(ProfileUiState(userId = userId))
        val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

        private val _events = MutableSharedFlow<ProfileEvent>()
        val events = _events.asSharedFlow()

        init {
            loadProfile()
        }

        private fun loadProfile() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    val isOwn = userId == "me"
                    val settings =
                        settingsServiceClient.getSettings(
                            GetSettingsRequest(userId = if (isOwn) "" else userId),
                        )

                    _uiState.update {
                        it.copy(
                            displayName = settings.displayName,
                            phoneNumber = settings.phoneNumber,
                            avatarUrl = settings.avatarUrl,
                            bio = settings.bio,
                            isOwnProfile = isOwn,
                            isLoading = false,
                            editDisplayName = settings.displayName,
                            editBio = settings.bio,
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load profile", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load profile",
                            isOwnProfile = userId == "me",
                        )
                    }
                }
            }
        }

        fun toggleEditMode() {
            _uiState.update { current ->
                if (current.isEditing) {
                    // Cancel editing, restore original values
                    current.copy(
                        isEditing = false,
                        editDisplayName = current.displayName,
                        editBio = current.bio,
                    )
                } else {
                    current.copy(isEditing = true)
                }
            }
        }

        fun onEditDisplayNameChanged(name: String) {
            _uiState.update { it.copy(editDisplayName = name) }
        }

        fun onEditBioChanged(bio: String) {
            _uiState.update { it.copy(editBio = bio) }
        }

        fun saveProfile() {
            val current = _uiState.value
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true) }
                try {
                    val response =
                        settingsServiceClient.updateProfile(
                            UpdateProfileRequest(
                                displayName = current.editDisplayName,
                                bio = current.editBio,
                                avatarUrl = current.avatarUrl,
                            ),
                        )
                    if (response.success) {
                        _uiState.update {
                            it.copy(
                                displayName = current.editDisplayName,
                                bio = current.editBio,
                                isSaving = false,
                                isEditing = false,
                            )
                        }
                        _events.emit(ProfileEvent.ProfileSaved)
                    } else {
                        _uiState.update { it.copy(isSaving = false) }
                        _events.emit(ProfileEvent.Error("Failed to save profile"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save profile", e)
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(ProfileEvent.Error(e.message ?: "Save failed"))
                }
            }
        }

        fun uploadAvatar(
            fileName: String,
            contentType: String,
            sizeBytes: Long,
        ) {
            viewModelScope.launch {
                try {
                    val presigned =
                        mediaServiceClient.getUploadUrl(
                            GetUploadUrlRequest(
                                fileName = fileName,
                                contentType = contentType,
                                sizeBytes = sizeBytes,
                                purpose = "avatar",
                            ),
                        )
                    _events.emit(ProfileEvent.AvatarUploadReady(presigned.url, presigned.mediaId))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get avatar upload URL", e)
                    _events.emit(ProfileEvent.Error("Avatar upload failed"))
                }
            }
        }

        fun onAvatarUploaded(avatarUrl: String) {
            _uiState.update { it.copy(avatarUrl = avatarUrl) }
            // Also update backend
            viewModelScope.launch {
                try {
                    val current = _uiState.value
                    settingsServiceClient.updateProfile(
                        UpdateProfileRequest(
                            displayName = current.displayName,
                            bio = current.bio,
                            avatarUrl = avatarUrl,
                        ),
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update avatar on backend", e)
                }
            }
        }
    }

sealed interface ProfileEvent {
    data object ProfileSaved : ProfileEvent

    data class AvatarUploadReady(
        val url: String,
        val mediaId: String,
    ) : ProfileEvent

    data class Error(
        val message: String,
    ) : ProfileEvent
}
