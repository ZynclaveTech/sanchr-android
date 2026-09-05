package com.sanchr.feature.profile

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.crypto.profile.EncryptedProfileUpdater
import com.sanchr.core.network.media.AvatarUploader
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.proto.settings.GetSettingsRequest
import com.sanchr.proto.settings.SettingsServiceClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
    val isUploadingAvatar: Boolean = false,
    val editDisplayName: String = "",
    val editBio: String = "",
    val errorMessage: String? = null,
    /** Whether we have blocked this person (other users' profiles only). */
    val isBlocked: Boolean = false,
)

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val settingsServiceClient: SettingsServiceClient,
        private val avatarUploader: AvatarUploader,
        private val profileUpdater: EncryptedProfileUpdater,
        private val contactRepository: ContactRepository,
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
                    val blocked = if (isOwn) false else runCatching { contactRepository.isBlocked(userId) }.getOrDefault(false)
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
                            isBlocked = blocked,
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

        /** Blocks or unblocks this person; enforced locally at once and told to the server (best effort). */
        fun setBlocked(blocked: Boolean) {
            if (_uiState.value.isOwnProfile) return
            viewModelScope.launch {
                try {
                    contactRepository.setBlocked(userId, blocked)
                    _uiState.update { it.copy(isBlocked = blocked) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = e.message ?: "Could not update block") }
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
                    // Encrypted under our Profile Key; the server never sees
                    // the name or bio in the clear. See EncryptedProfileUpdater.
                    val response =
                        profileUpdater.update(
                            displayName = current.editDisplayName,
                            bio = current.editBio,
                            avatarUrl = current.avatarUrl,
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

        /**
         * Uploads a new photo and points the profile at it. The two steps
         * are one operation to the user: if the profile write fails the
         * photo is still up on the CDN but nobody is told about it, so the
         * old avatar stays and an error is shown, rather than the UI
         * flipping to a photo the server does not know about.
         */
        fun uploadAvatar(
            bytes: ByteArray,
            contentType: String,
        ) {
            if (_uiState.value.isUploadingAvatar) return
            _uiState.update { it.copy(isUploadingAvatar = true, errorMessage = null) }
            viewModelScope.launch {
                try {
                    val avatarUrl = avatarUploader.upload(bytes, contentType)
                    val current = _uiState.value
                    profileUpdater.update(
                        displayName = current.displayName,
                        bio = current.bio,
                        avatarUrl = avatarUrl,
                    )
                    _uiState.update { it.copy(avatarUrl = avatarUrl, isUploadingAvatar = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Avatar upload failed", e)
                    _uiState.update { it.copy(isUploadingAvatar = false, errorMessage = "Avatar upload failed") }
                    _events.emit(ProfileEvent.Error("Avatar upload failed"))
                }
            }
        }
    }

sealed interface ProfileEvent {
    data object ProfileSaved : ProfileEvent

    data class Error(
        val message: String,
    ) : ProfileEvent
}
