package com.sanchr.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.notifications.PushTokenManager
import com.sanchr.proto.notifications.NotificationServiceClient
import com.sanchr.proto.notifications.UpdateNotificationPrefsRequest
import com.sanchr.proto.settings.GetSettingsRequest
import com.sanchr.proto.settings.GetStorageUsageRequest
import com.sanchr.proto.settings.SettingsServiceClient
import com.sanchr.proto.settings.StorageUsageResponse
import com.sanchr.proto.settings.ToggleVyncModeRequest
import com.sanchr.proto.settings.UpdateProfileRequest
import com.sanchr.proto.settings.UpdateSettingsRequest
import com.sanchr.proto.settings.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val displayName: String = "",
    val phoneNumber: String = "",
    val avatarUrl: String = "",
    val bio: String = "",
    val themeMode: String = "system",
    val dynamicColorEnabled: Boolean = false,
    val fontSize: String = "medium",
    val notificationsEnabled: Boolean = true,
    val notificationPreview: String = "always",
    val messageNotificationsEnabled: Boolean = true,
    val callNotificationsEnabled: Boolean = true,
    val groupNotificationsEnabled: Boolean = true,
    val notificationSoundEnabled: Boolean = true,
    val notificationVibrationEnabled: Boolean = true,
    val readReceiptsEnabled: Boolean = true,
    val typingIndicatorsEnabled: Boolean = true,
    val lastActiveVisible: Boolean = true,
    val profilePhotoVisible: Boolean = true,
    val biometricEnabled: Boolean = false,
    val screenLockEnabled: Boolean = false,
    val screenLockTimeout: String = "immediately",
    val screenshotProtection: Boolean = false,
    val vyncModeEnabled: Boolean = false,
    val mediaAutoDownload: String = "wifi",
    val lowDataMode: Boolean = false,
    val storageUsage: StorageUsageResponse? = null,
    val enterSendsMessage: Boolean = false,
    val mediaAutoSave: Boolean = true,
    val bubbleStyle: String = "default",
    val disappearingMessagesDefault: String = "off",
    val isSyncingPrefs: Boolean = false,
    val isLoadingSettings: Boolean = true,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val sessionManager: SessionManager,
    private val notificationServiceClient: NotificationServiceClient,
    private val pushTokenManager: PushTokenManager,
    private val settingsServiceClient: SettingsServiceClient,
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _remoteSettings = MutableStateFlow<UserSettings?>(null)
    private val _storageUsage = MutableStateFlow<StorageUsageResponse?>(null)
    private val _isLoadingSettings = MutableStateFlow(true)
    private val _screenLockEnabled = MutableStateFlow(false)
    private val _screenLockTimeout = MutableStateFlow("immediately")
    private val _screenshotProtection = MutableStateFlow(false)
    private val _enterSendsMessage = MutableStateFlow(false)
    private val _mediaAutoSave = MutableStateFlow(true)
    private val _bubbleStyle = MutableStateFlow("default")
    private val _disappearingMessagesDefault = MutableStateFlow("off")
    private val _lowDataMode = MutableStateFlow(false)

    private val _pendingSync = MutableStateFlow(0L)

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            userPreferences.themeMode,
            userPreferences.dynamicColorEnabled,
            userPreferences.notificationsEnabled,
            userPreferences.readReceiptsEnabled,
            userPreferences.biometricEnabled,
        ) { theme, dynamic, notifications, readReceipts, biometric ->
            PrefsGroup(theme, dynamic, notifications, readReceipts, biometric)
        },
        _remoteSettings,
        _storageUsage,
        _isLoadingSettings,
        combine(
            _screenLockEnabled,
            _screenshotProtection,
            _enterSendsMessage,
            _lowDataMode,
            _disappearingMessagesDefault,
        ) { lock, screenshot, enter, lowData, disappear ->
            ExtrasGroup(lock, screenshot, enter, lowData, disappear)
        },
    ) { prefs, remote, storage, loading, extras ->
        SettingsUiState(
            displayName = remote?.displayName ?: "",
            phoneNumber = remote?.phoneNumber ?: "",
            avatarUrl = remote?.avatarUrl ?: "",
            bio = remote?.bio ?: "",
            themeMode = prefs.themeMode,
            dynamicColorEnabled = prefs.dynamicColorEnabled,
            fontSize = remote?.fontSize ?: "medium",
            notificationsEnabled = prefs.notificationsEnabled,
            readReceiptsEnabled = prefs.readReceiptsEnabled,
            typingIndicatorsEnabled = remote?.typingIndicatorsEnabled ?: true,
            lastActiveVisible = remote?.lastSeenVisible ?: true,
            profilePhotoVisible = remote?.profilePhotoVisible ?: true,
            biometricEnabled = prefs.biometricEnabled,
            screenLockEnabled = extras.screenLockEnabled,
            screenshotProtection = extras.screenshotProtection,
            vyncModeEnabled = remote?.vyncModeEnabled ?: false,
            mediaAutoDownload = remote?.autoDownloadMedia?.let { if (it) "always" else "wifi" } ?: "wifi",
            lowDataMode = extras.lowDataMode,
            storageUsage = storage,
            enterSendsMessage = extras.enterSendsMessage,
            mediaAutoSave = _mediaAutoSave.value,
            bubbleStyle = _bubbleStyle.value,
            disappearingMessagesDefault = extras.disappearingMessagesDefault,
            isLoadingSettings = loading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    init {
        loadSettingsFromBackend()
        loadStorageUsage()
        observeDebouncedSync()
    }

    private fun loadSettingsFromBackend() {
        viewModelScope.launch {
            try {
                val settings = settingsServiceClient.getSettings(
                    GetSettingsRequest(userId = ""),
                )
                _remoteSettings.value = settings
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load settings from backend", e)
            } finally {
                _isLoadingSettings.value = false
            }
        }
    }

    private fun loadStorageUsage() {
        viewModelScope.launch {
            try {
                val usage = settingsServiceClient.getStorageUsage(
                    GetStorageUsageRequest(userId = ""),
                )
                _storageUsage.value = usage
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load storage usage", e)
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeDebouncedSync() {
        viewModelScope.launch {
            _pendingSync
                .debounce(1_000)
                .collect { timestamp ->
                    if (timestamp > 0L) {
                        syncSettingsToBackend()
                    }
                }
        }
    }

    private fun triggerDebouncedSync() {
        _pendingSync.value = System.currentTimeMillis()
    }

    private suspend fun syncSettingsToBackend() {
        val remote = _remoteSettings.value ?: return
        try {
            settingsServiceClient.updateSettings(
                UpdateSettingsRequest(settings = remote),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync settings", e)
        }
    }

    // --- Theme ---

    fun setThemeMode(mode: String) {
        viewModelScope.launch { userPreferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setDynamicColor(enabled) }
    }

    fun setFontSize(size: String) {
        _remoteSettings.update { it?.copy(fontSize = size) }
        triggerDebouncedSync()
    }

    // --- Notifications ---

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setNotificationsEnabled(enabled)
            syncNotificationPrefsToBackend()
        }
    }

    fun setMessageNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            syncNotificationPrefsToBackend(messagesEnabled = enabled)
        }
    }

    fun setCallNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            syncNotificationPrefsToBackend(callsEnabled = enabled)
        }
    }

    fun setGroupNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            syncNotificationPrefsToBackend(groupEnabled = enabled)
        }
    }

    fun setNotificationPreview(preview: String) {
        viewModelScope.launch {
            userPreferences.setNotificationPreview(preview)
            val showPreview = preview != "never"
            syncNotificationPrefsToBackend(showPreview = showPreview)
        }
    }

    fun setNotificationSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            syncNotificationPrefsToBackend(soundEnabled = enabled)
        }
    }

    fun setNotificationVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            syncNotificationPrefsToBackend(vibrationEnabled = enabled)
        }
    }

    // --- Privacy ---

    fun setReadReceiptsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setReadReceiptsEnabled(enabled)
            _remoteSettings.update { it?.copy(readReceiptsEnabled = enabled) }
            triggerDebouncedSync()
        }
    }

    fun setTypingIndicatorsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setTypingIndicatorsEnabled(enabled)
            _remoteSettings.update { it?.copy(typingIndicatorsEnabled = enabled) }
            triggerDebouncedSync()
        }
    }

    fun setLastActiveVisible(visible: Boolean) {
        viewModelScope.launch {
            userPreferences.setLastActiveVisible(visible)
            _remoteSettings.update { it?.copy(lastSeenVisible = visible) }
            triggerDebouncedSync()
        }
    }

    fun setProfilePhotoVisible(visible: Boolean) {
        _remoteSettings.update { it?.copy(profilePhotoVisible = visible) }
        triggerDebouncedSync()
    }

    fun setDisappearingMessagesDefault(duration: String) {
        _disappearingMessagesDefault.value = duration
    }

    // --- Security ---

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setBiometricEnabled(enabled) }
    }

    fun setScreenLockEnabled(enabled: Boolean) {
        _screenLockEnabled.value = enabled
    }

    fun setScreenLockTimeout(timeout: String) {
        _screenLockTimeout.value = timeout
    }

    fun setScreenshotProtection(enabled: Boolean) {
        _screenshotProtection.value = enabled
    }

    fun toggleVyncMode(enabled: Boolean, pin: String = "") {
        viewModelScope.launch {
            try {
                val updated = settingsServiceClient.toggleVyncMode(
                    ToggleVyncModeRequest(enabled = enabled, pin = pin),
                )
                _remoteSettings.value = updated
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle VyncMode", e)
                _events.emit(SettingsEvent.Error("Failed to toggle VyncMode"))
            }
        }
    }

    // --- Storage ---

    fun setMediaAutoDownload(policy: String) {
        viewModelScope.launch { userPreferences.setMediaAutoDownload(policy) }
        _remoteSettings.update { it?.copy(autoDownloadMedia = policy == "always") }
        triggerDebouncedSync()
    }

    fun setLowDataMode(enabled: Boolean) {
        _lowDataMode.value = enabled
    }

    fun clearCache() {
        viewModelScope.launch {
            _events.emit(SettingsEvent.CacheCleared)
            loadStorageUsage()
        }
    }

    // --- Chat Settings ---

    fun setBubbleStyle(style: String) {
        _bubbleStyle.value = style
    }

    fun setEnterSendsMessage(enabled: Boolean) {
        _enterSendsMessage.value = enabled
    }

    fun setMediaAutoSave(enabled: Boolean) {
        _mediaAutoSave.value = enabled
    }

    // --- Profile ---

    fun updateProfile(displayName: String, bio: String) {
        viewModelScope.launch {
            try {
                settingsServiceClient.updateProfile(
                    UpdateProfileRequest(
                        displayName = displayName,
                        bio = bio,
                        avatarUrl = _remoteSettings.value?.avatarUrl ?: "",
                    ),
                )
                _remoteSettings.update {
                    it?.copy(displayName = displayName, bio = bio)
                }
                _events.emit(SettingsEvent.ProfileUpdated)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update profile", e)
                _events.emit(SettingsEvent.Error("Failed to update profile"))
            }
        }
    }

    // --- Logout ---

    fun logout() {
        viewModelScope.launch {
            try {
                pushTokenManager.clearToken()
            } catch (_: Exception) {
                // Non-fatal
            }
            sessionManager.clearSession()
        }
    }

    // --- Backend sync helpers ---

    private suspend fun syncNotificationPrefsToBackend(
        messagesEnabled: Boolean? = null,
        callsEnabled: Boolean? = null,
        groupEnabled: Boolean? = null,
        soundEnabled: Boolean? = null,
        vibrationEnabled: Boolean? = null,
        showPreview: Boolean? = null,
    ) {
        val current = uiState.value
        try {
            notificationServiceClient.updateNotificationPrefs(
                UpdateNotificationPrefsRequest(
                    messagesEnabled = messagesEnabled ?: current.messageNotificationsEnabled,
                    callsEnabled = callsEnabled ?: current.callNotificationsEnabled,
                    showPreview = showPreview ?: (current.notificationPreview != "never"),
                    soundEnabled = soundEnabled ?: current.notificationSoundEnabled,
                    vibrationEnabled = vibrationEnabled ?: current.notificationVibrationEnabled,
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync notification prefs to backend", e)
        }
    }
}

sealed interface SettingsEvent {
    data object ProfileUpdated : SettingsEvent
    data object CacheCleared : SettingsEvent
    data class Error(val message: String) : SettingsEvent
}

private data class PrefsGroup(
    val themeMode: String,
    val dynamicColorEnabled: Boolean,
    val notificationsEnabled: Boolean,
    val readReceiptsEnabled: Boolean,
    val biometricEnabled: Boolean,
)

private data class ExtrasGroup(
    val screenLockEnabled: Boolean,
    val screenshotProtection: Boolean,
    val enterSendsMessage: Boolean,
    val lowDataMode: Boolean,
    val disappearingMessagesDefault: String,
)
