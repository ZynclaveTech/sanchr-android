package com.sanchr.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

/**
 * Manages user preferences using Jetpack DataStore.
 * Stores non-sensitive settings like theme, notification preferences, etc.
 */
@Singleton
class UserPreferences
    @Inject
    constructor(
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
            val NOTIFICATION_LOCKSCREEN_PREVIEW = booleanPreferencesKey("notification_lockscreen_preview")
            val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
            val SCREENSHOT_PROTECTION_ENABLED = booleanPreferencesKey("screenshot_protection_enabled")
            val SCREEN_LOCK_TIMEOUT = intPreferencesKey("screen_lock_timeout_seconds")
            val ONLINE_STATUS_VISIBLE = booleanPreferencesKey("last_active_visible")
            val READ_RECEIPTS_ENABLED = booleanPreferencesKey("read_receipts_enabled")
            val TYPING_INDICATORS_ENABLED = booleanPreferencesKey("typing_indicators_enabled")
            val DISAPPEARING_DEFAULT_DURATION = intPreferencesKey("disappearing_default_duration_seconds")
            val FONT_SIZE_SCALE = intPreferencesKey("font_size_scale") // percentage: 80, 100, 120
            val MEDIA_AUTO_DOWNLOAD = stringPreferencesKey("media_auto_download") // "wifi", "always", "never"
            val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
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

        /**
         * If true, message notifications render their full sender + preview on
         * the lockscreen (NotificationCompat.VISIBILITY_PUBLIC). Default false
         * — the lockscreen gets the generic public-version notification
         * ("New message") only, matching the iOS private-by-default posture.
         */
        val showPreviewOnLockscreen: Flow<Boolean> =
            dataStore.data.map { it[Keys.NOTIFICATION_LOCKSCREEN_PREVIEW] ?: false }

        suspend fun setShowPreviewOnLockscreen(enabled: Boolean) {
            dataStore.edit { it[Keys.NOTIFICATION_LOCKSCREEN_PREVIEW] = enabled }
        }

        // --- Privacy ---
        val readReceiptsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.READ_RECEIPTS_ENABLED] ?: true }

        suspend fun setReadReceiptsEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.READ_RECEIPTS_ENABLED] = enabled }
        }

        /**
         * Default disappearing-message timer for conversations that set none
         * of their own, in seconds; `0` means off. Canonical unit is seconds
         * because that is what travels on the wire as
         * `InnerPayload.expiresAfterSecs` — the UI's "24h"-style labels are a
         * presentation concern and are mapped at the settings layer.
         */
        val disappearingDefaultSeconds: Flow<Int> = dataStore.data.map { it[Keys.DISAPPEARING_DEFAULT_DURATION] ?: 0 }

        suspend fun setDisappearingDefaultSeconds(seconds: Int) {
            dataStore.edit { it[Keys.DISAPPEARING_DEFAULT_DURATION] = seconds.coerceAtLeast(0) }
        }

        val typingIndicatorsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.TYPING_INDICATORS_ENABLED] ?: true }

        suspend fun setTypingIndicatorsEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.TYPING_INDICATORS_ENABLED] = enabled }
        }

        val onlineStatusVisible: Flow<Boolean> =
            dataStore.data.map {
                it[Keys.ONLINE_STATUS_VISIBLE] ?: true
            }

        suspend fun setOnlineStatusVisible(visible: Boolean) {
            dataStore.edit { it[Keys.ONLINE_STATUS_VISIBLE] = visible }
        }

        // --- Security ---
        val biometricEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.BIOMETRIC_ENABLED] ?: false }

        suspend fun setBiometricEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
        }

        /**
         * User-controlled general screenshot protection. When true the hosting
         * Activity applies `WindowManager.LayoutParams.FLAG_SECURE` to the
         * window, which blocks screenshots, screen recording, and redacts the
         * app in the recents/app-switcher thumbnail.
         *
         * Disabled by default so the app behaves normally out of the box;
         * privacy-conscious users opt in from Settings → Security. Sensitive
         * screens (OTP, recovery-key) still force FLAG_SECURE unconditionally
         * via `SecureScreen()` regardless of this preference.
         */
        val screenshotProtectionEnabled: Flow<Boolean> =
            dataStore.data.map { it[Keys.SCREENSHOT_PROTECTION_ENABLED] ?: false }

        suspend fun setScreenshotProtectionEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.SCREENSHOT_PROTECTION_ENABLED] = enabled }
        }

        // --- Media ---
        val mediaAutoDownload: Flow<String> = dataStore.data.map { it[Keys.MEDIA_AUTO_DOWNLOAD] ?: "wifi" }

        suspend fun setMediaAutoDownload(policy: String) {
            dataStore.edit { it[Keys.MEDIA_AUTO_DOWNLOAD] = policy }
        }

        // --- Onboarding ---

        /**
         * Per-device flag gating the first-run onboarding flow (Welcome → Name →
         * Avatar → ContactSync). Default false so fresh installs land on
         * onboarding after OTP verify; flipped true by
         * [setOnboardingCompleted] once `OnboardingViewModel.onContactSyncFinish`
         * fires. iOS parity: `SanchrApp.swift:331` uses a per-device
         * `@AppStorage("sanchr.activeOnboardingFlow")` flag combined with a
         * display-name check — Android chooses per-device DataStore only
         * (see plan §Step A). Returning users on a new device will see
         * onboarding once — acceptable per Phase 5 design.
         *
         * On logout [clear] wipes this flag (alongside every other key) so a
         * subsequent re-login with a different account on the same device
         * re-runs onboarding correctly instead of silently inheriting the
         * previous user's completion state.
         */
        val hasCompletedOnboardingFlow: Flow<Boolean> =
            dataStore.data.map { it[Keys.HAS_COMPLETED_ONBOARDING] ?: false }

        suspend fun setOnboardingCompleted(completed: Boolean) {
            dataStore.edit { it[Keys.HAS_COMPLETED_ONBOARDING] = completed }
        }

        // --- Lifecycle ---

        /**
         * Wipes all user-level preferences. Called from `LogoutUseCase` so
         * per-user flags (onboarding completion, privacy toggles, etc.) do
         * not leak across accounts when a second user logs in on the same
         * device. Device-global defaults re-materialise on the next read via
         * the `?: <default>` fallbacks on each flow.
         */
        suspend fun clear() {
            dataStore.edit { it.clear() }
        }
    }
