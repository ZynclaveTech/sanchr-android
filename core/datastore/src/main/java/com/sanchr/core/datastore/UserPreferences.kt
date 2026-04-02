package com.sanchr.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

/**
 * Manages user preferences using Jetpack DataStore.
 * Stores non-sensitive settings like theme, notification preferences, etc.
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.userPreferencesDataStore

    // --- Keys ---
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode") // "system", "light", "dark"
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val NOTIFICATION_SOUND = booleanPreferencesKey("notification_sound")
        val NOTIFICATION_VIBRATE = booleanPreferencesKey("notification_vibrate")
        val NOTIFICATION_PREVIEW = stringPreferencesKey("notification_preview") // "always", "contacts", "never"
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val SCREEN_LOCK_TIMEOUT = intPreferencesKey("screen_lock_timeout_seconds")
        val LAST_ACTIVE_VISIBLE = booleanPreferencesKey("last_active_visible")
        val READ_RECEIPTS_ENABLED = booleanPreferencesKey("read_receipts_enabled")
        val TYPING_INDICATORS_ENABLED = booleanPreferencesKey("typing_indicators_enabled")
        val DISAPPEARING_DEFAULT_DURATION = intPreferencesKey("disappearing_default_duration_seconds")
        val FONT_SIZE_SCALE = intPreferencesKey("font_size_scale") // percentage: 80, 100, 120
        val MEDIA_AUTO_DOWNLOAD = stringPreferencesKey("media_auto_download") // "wifi", "always", "never"
    }

    // --- Theme ---
    val themeMode: Flow<String> = dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    val dynamicColorEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: false }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    // --- Notifications ---
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFICATION_ENABLED] ?: true }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATION_ENABLED] = enabled }
    }

    val notificationPreview: Flow<String> = dataStore.data.map { it[Keys.NOTIFICATION_PREVIEW] ?: "always" }

    suspend fun setNotificationPreview(preview: String) {
        dataStore.edit { it[Keys.NOTIFICATION_PREVIEW] = preview }
    }

    // --- Privacy ---
    val readReceiptsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.READ_RECEIPTS_ENABLED] ?: true }

    suspend fun setReadReceiptsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.READ_RECEIPTS_ENABLED] = enabled }
    }

    val typingIndicatorsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.TYPING_INDICATORS_ENABLED] ?: true }

    suspend fun setTypingIndicatorsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.TYPING_INDICATORS_ENABLED] = enabled }
    }

    val lastActiveVisible: Flow<Boolean> = dataStore.data.map { it[Keys.LAST_ACTIVE_VISIBLE] ?: true }

    suspend fun setLastActiveVisible(visible: Boolean) {
        dataStore.edit { it[Keys.LAST_ACTIVE_VISIBLE] = visible }
    }

    // --- Security ---
    val biometricEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.BIOMETRIC_ENABLED] ?: false }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    // --- Media ---
    val mediaAutoDownload: Flow<String> = dataStore.data.map { it[Keys.MEDIA_AUTO_DOWNLOAD] ?: "wifi" }

    suspend fun setMediaAutoDownload(policy: String) {
        dataStore.edit { it[Keys.MEDIA_AUTO_DOWNLOAD] = policy }
    }
}
