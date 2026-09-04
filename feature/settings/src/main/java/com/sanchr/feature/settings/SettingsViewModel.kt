package com.sanchr.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.common.Result
import com.sanchr.core.crypto.RecoveryKeyManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.notifications.PushTokenManager
import com.sanchr.domain.messaging.DeleteAccountUseCase
import com.sanchr.proto.notifications.NotificationServiceClient
import com.sanchr.proto.notifications.UpdateNotificationPrefsRequest
import com.sanchr.proto.settings.GetSettingsRequest
import com.sanchr.proto.settings.GetStorageUsageRequest
import com.sanchr.proto.settings.SettingsServiceClient
import com.sanchr.proto.settings.StorageUsageResponse
import com.sanchr.proto.settings.ToggleSanchrModeRequest
import com.sanchr.proto.settings.UpdateProfileRequest
import com.sanchr.proto.settings.UpdateSettingsRequest
import com.sanchr.proto.settings.UserSettings
import com.sanchr.sync.backup.ChatBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val onlineStatusVisible: Boolean = true,
    val profilePhotoVisible: Boolean = true,
    val biometricEnabled: Boolean = false,
    val screenLockEnabled: Boolean = false,
    val screenLockTimeout: String = "immediately",
    val screenshotProtection: Boolean = false,
    val sanchrModeEnabled: Boolean = false,
    val mediaAutoDownload: String = "wifi",
    val lowDataMode: Boolean = false,
    val storageUsage: StorageUsageResponse? = null,
    val enterSendsMessage: Boolean = false,
    val mediaAutoSave: Boolean = true,
    val bubbleStyle: String = "default",
    val backupEnabled: Boolean = false,
    val lastBackupAtMillis: Long? = null,
    val isBackupBusy: Boolean = false,
    val disappearingMessagesDefault: String = "off",
    val isSyncingPrefs: Boolean = false,
    val isLoadingSettings: Boolean = true,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val userPreferences: UserPreferences,
        private val recoveryKeyManager: RecoveryKeyManager,
        private val notificationServiceClient: NotificationServiceClient,
        private val pushTokenManager: PushTokenManager,
        private val settingsServiceClient: SettingsServiceClient,
        private val chatBackupManager: ChatBackupManager,
        private val deleteAccountUseCase: DeleteAccountUseCase,
    ) : ViewModel() {
        companion object {
            private const val TAG = "SettingsViewModel"
        }

        private val _remoteSettings = MutableStateFlow<UserSettings?>(null)
        private val _storageUsage = MutableStateFlow<StorageUsageResponse?>(null)
        private val _isLoadingSettings = MutableStateFlow(true)
        private val _screenLockEnabled = MutableStateFlow(false)
        private val _screenLockTimeout = MutableStateFlow("immediately")
        private val _enterSendsMessage = MutableStateFlow(false)
        private val _mediaAutoSave = MutableStateFlow(true)
        private val _bubbleStyle = MutableStateFlow("default")
        private val _backupConfiguration = MutableStateFlow(recoveryKeyManager.loadConfiguration())
        private val _backupBusy = MutableStateFlow(false)
        private val _pendingRecoveryKey = MutableStateFlow<String?>(null)
        private val _accountDeletion = MutableStateFlow<AccountDeletionState>(AccountDeletionState.Idle)
        val accountDeletion: StateFlow<AccountDeletionState> = _accountDeletion.asStateFlow()
        private val _disappearingMessagesDefault = MutableStateFlow("off")
        private val _lowDataMode = MutableStateFlow(false)

        private val _pendingSync = MutableStateFlow(0L)

        private val _events = MutableSharedFlow<SettingsEvent>()
        val events = _events.asSharedFlow()

        val uiState: StateFlow<SettingsUiState> =
            combine(
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
                    combine(
                        _screenLockEnabled,
                        userPreferences.screenshotProtectionEnabled,
                        _enterSendsMessage,
                        _lowDataMode,
                        _disappearingMessagesDefault,
                    ) { lock, screenshot, enter, lowData, disappear ->
                        BasicExtrasGroup(lock, screenshot, enter, lowData, disappear)
                    },
                    combine(_backupConfiguration, _backupBusy) { backupConfiguration, backupBusy ->
                        BackupUiGroup(backupConfiguration, backupBusy)
                    },
                ) { basic, backup ->
                    ExtrasGroup(
                        screenLockEnabled = basic.screenLockEnabled,
                        screenshotProtection = basic.screenshotProtection,
                        enterSendsMessage = basic.enterSendsMessage,
                        lowDataMode = basic.lowDataMode,
                        disappearingMessagesDefault = basic.disappearingMessagesDefault,
                        backupConfiguration = backup.configuration,
                        backupBusy = backup.isBusy,
                    )
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
                    onlineStatusVisible = remote?.onlineStatusVisible ?: true,
                    profilePhotoVisible = remote?.profilePhotoVisible ?: true,
                    biometricEnabled = prefs.biometricEnabled,
                    screenLockEnabled = extras.screenLockEnabled,
                    screenshotProtection = extras.screenshotProtection,
                    sanchrModeEnabled = remote?.sanchrModeEnabled ?: false,
                    mediaAutoDownload = remote?.autoDownloadMedia?.let { if (it) "always" else "wifi" } ?: "wifi",
                    lowDataMode = extras.lowDataMode,
                    storageUsage = storage,
                    enterSendsMessage = extras.enterSendsMessage,
                    mediaAutoSave = _mediaAutoSave.value,
                    bubbleStyle = _bubbleStyle.value,
                    backupEnabled = extras.backupConfiguration?.isEnabled ?: false,
                    lastBackupAtMillis = extras.backupConfiguration?.lastBackupAtMillis,
                    isBackupBusy = extras.backupBusy,
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
                    val settings =
                        settingsServiceClient.getSettings(
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
                    val usage =
                        settingsServiceClient.getStorageUsage(
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

        fun setOnlineStatusVisible(visible: Boolean) {
            viewModelScope.launch {
                userPreferences.setOnlineStatusVisible(visible)
                _remoteSettings.update { it?.copy(onlineStatusVisible = visible) }
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

        /**
         * Persists the user's global screenshot-protection preference.
         * `MainActivity` observes `UserPreferences.screenshotProtectionEnabled`
         * and applies `FLAG_SECURE` to the window reactively, so this is the
         * only call site needed to flip the state across the whole app.
         */
        fun setScreenshotProtection(enabled: Boolean) {
            viewModelScope.launch { userPreferences.setScreenshotProtectionEnabled(enabled) }
        }

        fun toggleSanchrMode(enabled: Boolean) {
            viewModelScope.launch {
                try {
                    val updated =
                        settingsServiceClient.toggleSanchrMode(
                            ToggleSanchrModeRequest(enabled = enabled),
                        )
                    _remoteSettings.value = updated
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to toggle Sanchr Mode", e)
                    _events.emit(SettingsEvent.Error("Failed to toggle Sanchr Mode"))
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

        fun beginBackupEnable() {
            try {
                _pendingRecoveryKey.value = recoveryKeyManager.generateRecoveryKey()
            } catch (error: Exception) {
                viewModelScope.launch {
                    _events.emit(SettingsEvent.Error("Failed to generate recovery key"))
                }
            }
        }

        fun pendingRecoveryKey(): String? = _pendingRecoveryKey.value

        fun cancelPendingBackup() {
            _pendingRecoveryKey.value = null
        }

        fun confirmPendingBackup() {
            val recoveryKey = _pendingRecoveryKey.value ?: return
            _backupConfiguration.value = recoveryKeyManager.enableBackups(recoveryKey)
            _pendingRecoveryKey.value = null
            viewModelScope.launch {
                runBackupAction {
                    chatBackupManager.backupNow()
                    _backupConfiguration.value = recoveryKeyManager.loadConfiguration()
                }
            }
        }

        fun disableBackup() {
            recoveryKeyManager.disableBackups()
            _backupConfiguration.value = null
        }

        fun revealRecoveryKey() {
            val recoveryKey = recoveryKeyManager.readRecoveryKey() ?: return
            viewModelScope.launch {
                _events.emit(SettingsEvent.RecoveryKeyRevealed(recoveryKey))
            }
        }

        fun rotateRecoveryKey() {
            beginBackupEnable()
        }

        fun backupNow() {
            viewModelScope.launch {
                runBackupAction {
                    chatBackupManager.backupNow()
                    _backupConfiguration.value = recoveryKeyManager.loadConfiguration()
                    _events.emit(SettingsEvent.BackupCompleted)
                }
            }
        }

        fun restoreBackup(recoveryKey: String?) {
            viewModelScope.launch {
                runBackupAction {
                    chatBackupManager.restoreLatestBackup(recoveryKey)
                    _backupConfiguration.value = recoveryKeyManager.loadConfiguration()
                    _events.emit(SettingsEvent.BackupRestored)
                }
            }
        }

        fun deleteRemoteBackups() {
            viewModelScope.launch {
                runBackupAction {
                    chatBackupManager.deleteRemoteBackups()
                    _backupConfiguration.value = recoveryKeyManager.loadConfiguration()
                }
            }
        }

        // --- Profile ---

        fun updateProfile(
            displayName: String,
            bio: String,
        ) {
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

        // --- Account deletion ---

        /**
         * Deleting the account is the only way out of Sanchr — there is
         * deliberately no sign-out — so this is the single exit path.
         *
         * [DeleteAccountUseCase] deletes server-side first and only wipes the
         * device once that succeeds, so a failed delete is retryable rather
         * than stranding a live account on a wiped device. On success the wipe
         * flips `SessionManager.sessionActive` to false, which `SanchrNavHost`
         * observes to navigate back to the auth graph — this ViewModel does
         * not navigate itself.
         *
         * Failure is surfaced through [accountDeletion] rather than
         * [SettingsEvent.Error]: `SettingsScreen` does not collect the event
         * flow, so an emitted event would be invisible to the user.
         */
        fun deleteAccount() {
            if (_accountDeletion.value is AccountDeletionState.InProgress) return
            // Flip to InProgress synchronously, before launching: setting it
            // inside the coroutine leaves a window where a second tap passes
            // the guard because neither body has run yet, and starts a second
            // delete.
            _accountDeletion.value = AccountDeletionState.InProgress
            viewModelScope.launch {
                // Deregister the FCM token first: a server-side side effect
                // that touches no local state. A failure here must not block
                // deletion — a stale token is far less dangerous than an
                // account the user believes is gone.
                try {
                    pushTokenManager.clearToken()
                } catch (_: Exception) {
                    // Non-fatal
                }
                _accountDeletion.value =
                    when (val result = deleteAccountUseCase()) {
                        is Result.Success -> AccountDeletionState.Idle
                        is Result.Error ->
                            AccountDeletionState.Failed(
                                result.exception.message ?: "Couldn't delete your account. Please try again.",
                            )
                        is Result.Loading -> AccountDeletionState.InProgress
                    }
            }
        }

        fun dismissAccountDeletionError() {
            _accountDeletion.value = AccountDeletionState.Idle
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
                        messageNotifications = messagesEnabled ?: current.messageNotificationsEnabled,
                        callNotifications = callsEnabled ?: current.callNotificationsEnabled,
                        // Proto does not distinguish group vs direct-message notifications on
                        // the Android client today; re-use the messages toggle until a
                        // dedicated UI setting lands.
                        groupNotifications = groupEnabled ?: messagesEnabled ?: current.messageNotificationsEnabled,
                        showPreview = showPreview ?: (current.notificationPreview != "never"),
                        // The proto carries a platform sound name instead of a boolean.
                        // Empty string = silent, non-empty = use that sound. Map the
                        // local boolean to the platform default name for now.
                        notificationSound = if (soundEnabled ?: current.notificationSoundEnabled) "default" else "",
                        vibrate = vibrationEnabled ?: current.notificationVibrationEnabled,
                    ),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync notification prefs to backend", e)
            }
        }

        private suspend fun runBackupAction(block: suspend () -> Unit) {
            _backupBusy.value = true
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Backup action failed", e)
                _events.emit(SettingsEvent.Error(e.message ?: "Backup action failed"))
            } finally {
                _backupBusy.value = false
            }
        }
    }

/** In-flight and failure state for the account-deletion flow. */
sealed interface AccountDeletionState {
    data object Idle : AccountDeletionState

    data object InProgress : AccountDeletionState

    data class Failed(
        val message: String,
    ) : AccountDeletionState
}

sealed interface SettingsEvent {
    data object ProfileUpdated : SettingsEvent

    data object CacheCleared : SettingsEvent

    data object BackupCompleted : SettingsEvent

    data object BackupRestored : SettingsEvent

    data class RecoveryKeyRevealed(
        val key: String,
    ) : SettingsEvent

    data class Error(
        val message: String,
    ) : SettingsEvent
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
    val backupConfiguration: com.sanchr.core.datastore.BackupConfiguration?,
    val backupBusy: Boolean,
)

private data class BackupUiGroup(
    val configuration: com.sanchr.core.datastore.BackupConfiguration?,
    val isBusy: Boolean,
)

private data class BasicExtrasGroup(
    val screenLockEnabled: Boolean,
    val screenshotProtection: Boolean,
    val enterSendsMessage: Boolean,
    val lowDataMode: Boolean,
    val disappearingMessagesDefault: String,
)
