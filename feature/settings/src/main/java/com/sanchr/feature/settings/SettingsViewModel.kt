package com.sanchr.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.notifications.PushTokenManager
import com.sanchr.proto.notifications.NotificationServiceClient
import com.sanchr.proto.notifications.UpdateNotificationPrefsRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: String = "system",
    val dynamicColorEnabled: Boolean = false,
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
    val biometricEnabled: Boolean = false,
    val mediaAutoDownload: String = "wifi",
    val isSyncingPrefs: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val sessionManager: SessionManager,
    private val notificationServiceClient: NotificationServiceClient,
    private val pushTokenManager: PushTokenManager,
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferences.themeMode,
        userPreferences.dynamicColorEnabled,
        userPreferences.notificationsEnabled,
        userPreferences.readReceiptsEnabled,
        userPreferences.biometricEnabled,
    ) { theme, dynamic, notifications, readReceipts, biometric ->
        SettingsUiState(
            themeMode = theme,
            dynamicColorEnabled = dynamic,
            notificationsEnabled = notifications,
            readReceiptsEnabled = readReceipts,
            biometricEnabled = biometric,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: String) {
        viewModelScope.launch { userPreferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setDynamicColor(enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setNotificationsEnabled(enabled)
            syncNotificationPrefsToBackend()
        }
    }

    fun setMessageNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // Persist locally -- in a full implementation this would be a distinct
            // DataStore key; for now we sync the intent to the backend.
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

    fun setReadReceiptsEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setReadReceiptsEnabled(enabled) }
    }

    fun setTypingIndicatorsEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setTypingIndicatorsEnabled(enabled) }
    }

    fun setLastActiveVisible(visible: Boolean) {
        viewModelScope.launch { userPreferences.setLastActiveVisible(visible) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setBiometricEnabled(enabled) }
    }

    fun setMediaAutoDownload(policy: String) {
        viewModelScope.launch { userPreferences.setMediaAutoDownload(policy) }
    }

    fun logout() {
        viewModelScope.launch {
            // Clear push token from backend before destroying the session
            try {
                pushTokenManager.clearToken()
            } catch (_: Exception) {
                // Non-fatal: session clear proceeds regardless
            }
            sessionManager.clearSession()
            // TODO: Navigate to auth screen, clear back stack
        }
    }

    // ------------------------------------------------------------------
    // Backend sync
    // ------------------------------------------------------------------

    /**
     * Pushes the current notification preferences to the backend via
     * [NotificationServiceClient.updateNotificationPrefs].
     *
     * Individual overrides can be supplied to optimistically apply a change
     * before the local state flow updates.
     */
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
