package com.sanchr.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    val readReceiptsEnabled: Boolean = true,
    val typingIndicatorsEnabled: Boolean = true,
    val lastActiveVisible: Boolean = true,
    val biometricEnabled: Boolean = false,
    val mediaAutoDownload: String = "wifi",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val sessionManager: SessionManager,
) : ViewModel() {

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
        viewModelScope.launch { userPreferences.setNotificationsEnabled(enabled) }
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
        sessionManager.clearSession()
        // TODO: Navigate to auth screen, clear back stack
    }
}
