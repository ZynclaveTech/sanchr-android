package com.sanchr.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

            /**
             * The three per-kind notification switches and the sound/vibrate
             * pair. They were pushed to the server and stored nowhere, so the
             * screen could not read back what the user had chosen: every
             * switch sprang straight back to on.
             */
            val NOTIFY_MESSAGES = booleanPreferencesKey("notify_messages")
            val NOTIFY_GROUPS = booleanPreferencesKey("notify_groups")
            val NOTIFY_CALLS = booleanPreferencesKey("notify_calls")
            val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
            val SCREENSHOT_PROTECTION_ENABLED = booleanPreferencesKey("screenshot_protection_enabled")
            val CHAT_WALLPAPER = stringPreferencesKey("chat_wallpaper")

            /**
             * Bubble shape. "modern", "classic" or "compact" — iOS's
             * vocabulary, read from `sanchr.chatBubbleStyle` there. Android
             * offered "default"/"rounded"/"flat", which iOS cannot interpret,
             * so the same account rendered differently on each phone.
             */
            val CHAT_BUBBLE_STYLE = stringPreferencesKey("chat_bubble_style")
            val CHAT_FONT_SIZE = stringPreferencesKey("chat_font_size")

            /** Enter sends rather than inserting a newline. True on iOS too. */
            val ENTER_SENDS_MESSAGE = booleanPreferencesKey("enter_sends_message")

            /** Save received media to the gallery automatically. */
            val MEDIA_AUTO_SAVE = booleanPreferencesKey("media_auto_save")

            /** Hold back anything that spends data until asked. */
            val LOW_DATA_MODE = booleanPreferencesKey("low_data_mode")
            val CRASH_REPORTING_ENABLED = booleanPreferencesKey("crash_reporting_enabled")
            val SCREEN_LOCK_ENABLED = booleanPreferencesKey("screen_lock_enabled")
            val SCREEN_LOCK_TIMEOUT = intPreferencesKey("screen_lock_timeout_seconds")
            val ONLINE_STATUS_VISIBLE = booleanPreferencesKey("last_active_visible")
            val READ_RECEIPTS_ENABLED = booleanPreferencesKey("read_receipts_enabled")
            val SANCHR_MODE_ENABLED = booleanPreferencesKey("sanchr_mode_enabled")
            val TYPING_INDICATORS_ENABLED = booleanPreferencesKey("typing_indicators_enabled")
            val LINK_PREVIEWS_ENABLED = booleanPreferencesKey("link_previews_enabled")

            // "everyone", "contacts", "nobody" — the vocabulary in
            // settings.proto's profile_photo_visibility, shared with iOS.
            val PROFILE_PHOTO_VISIBILITY = stringPreferencesKey("profile_photo_visibility")
            val DISAPPEARING_DEFAULT_DURATION = intPreferencesKey("disappearing_default_duration_seconds")
            val FONT_SIZE_SCALE = intPreferencesKey("font_size_scale") // percentage: 80, 100, 120
            val MEDIA_AUTO_DOWNLOAD = stringPreferencesKey("media_auto_download") // "wifi", "always", "never"
            val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
            val BLOCK_LIST_RESTORED = booleanPreferencesKey("block_list_restored")
            val PENDING_BLOCK_SYNC = stringSetPreferencesKey("pending_block_sync")
        }

        // --- Theme ---
        val themeMode: Flow<String> = dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }

        /**
         * These three were in-memory flows that nothing persisted and nothing
         * read: a toggle moved, the session honoured it in Settings alone, and
         * a relaunch forgot it. iOS stores all three and acts on them, so the
         * same switch did different things on each phone.
         */
        val enterSendsMessage: Flow<Boolean> = dataStore.data.map { it[Keys.ENTER_SENDS_MESSAGE] ?: true }

        val mediaAutoSave: Flow<Boolean> = dataStore.data.map { it[Keys.MEDIA_AUTO_SAVE] ?: false }

        val lowDataMode: Flow<Boolean> = dataStore.data.map { it[Keys.LOW_DATA_MODE] ?: false }

        suspend fun setEnterSendsMessage(enabled: Boolean) {
            dataStore.edit { it[Keys.ENTER_SENDS_MESSAGE] = enabled }
        }

        suspend fun setMediaAutoSave(enabled: Boolean) {
            dataStore.edit { it[Keys.MEDIA_AUTO_SAVE] = enabled }
        }

        suspend fun setLowDataMode(enabled: Boolean) {
            dataStore.edit { it[Keys.LOW_DATA_MODE] = enabled }
        }

        /**
         * Whether media from one conversation may reach the system gallery
         * through Media Auto-Save. Per-conversation, visible by default, and
         * keyed exactly as iOS keys it in the App Group defaults
         * (`sanchr.chatMediaVisibility.<id>`) so the two platforms describe the
         * same switch.
         */
        fun mediaVisibleInGallery(conversationId: String): Flow<Boolean> =
            dataStore.data.map { it[mediaVisibilityKey(conversationId)] ?: true }

        suspend fun setMediaVisibleInGallery(
            conversationId: String,
            visible: Boolean,
        ) {
            dataStore.edit { it[mediaVisibilityKey(conversationId)] = visible }
        }

        private fun mediaVisibilityKey(conversationId: String) = booleanPreferencesKey("sanchr.chatMediaVisibility.$conversationId")

        /** The chosen bubble shape; "modern" is iOS's default too. */
        val chatBubbleStyle: Flow<String> = dataStore.data.map { it[Keys.CHAT_BUBBLE_STYLE] ?: "modern" }

        /**
         * The Font Size slider: "small", "medium", "large" or "extra_large".
         * Kept here because it is applied here, to the text of messages; it
         * used to live only in the copy of the server's settings, which
         * nothing that draws text ever saw.
         */
        val chatFontSize: Flow<String> = dataStore.data.map { it[Keys.CHAT_FONT_SIZE] ?: "medium" }

        suspend fun setChatFontSize(size: String) {
            dataStore.edit { it[Keys.CHAT_FONT_SIZE] = size }
        }

        suspend fun setChatBubbleStyle(style: String) {
            dataStore.edit { it[Keys.CHAT_BUBBLE_STYLE] = style }
        }

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

        val messageNotificationsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFY_MESSAGES] ?: true }

        val groupNotificationsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFY_GROUPS] ?: true }

        val callNotificationsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFY_CALLS] ?: true }

        val notificationSoundEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFICATION_SOUND] ?: true }

        val notificationVibrateEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFICATION_VIBRATE] ?: true }

        suspend fun setMessageNotificationsEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.NOTIFY_MESSAGES] = enabled }
        }

        suspend fun setGroupNotificationsEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.NOTIFY_GROUPS] = enabled }
        }

        suspend fun setCallNotificationsEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.NOTIFY_CALLS] = enabled }
        }

        suspend fun setNotificationSoundEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.NOTIFICATION_SOUND] = enabled }
        }

        suspend fun setNotificationVibrateEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.NOTIFICATION_VIBRATE] = enabled }
        }

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

        /**
         * Sanchr Mode: the one switch for "leave no trace of when I am here".
         *
         * It was a switch and nothing else. Turning it on saved a flag on the
         * server and changed nothing this app did: read receipts, typing and
         * presence all went out as before, and the screen stayed
         * capturable. On iOS it turns all three off and shields the screen,
         * whatever the individual toggles say.
         *
         * Kept on the device because it is enforced on the device, on every
         * send, including with no network. The individual toggles below keep
         * their own values underneath it, so turning the mode off again puts
         * things back as they were. What is actually allowed is in
         * [canSendReadReceipts], [canSendTypingIndicators], [canSendPresence]
         * and [screenSecurityActive]; code that sends or shields reads those,
         * and only the settings screens read the toggles themselves.
         */
        val sanchrModeEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.SANCHR_MODE_ENABLED] ?: false }

        suspend fun setSanchrModeEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.SANCHR_MODE_ENABLED] = enabled }
        }

        /** Whether a read receipt may be sent: the toggle, unless Sanchr Mode is on. */
        val canSendReadReceipts: Flow<Boolean> =
            dataStore.data.map { (it[Keys.READ_RECEIPTS_ENABLED] ?: true) && it[Keys.SANCHR_MODE_ENABLED] != true }

        /** Whether "typing" may be sent: the toggle, unless Sanchr Mode is on. */
        val canSendTypingIndicators: Flow<Boolean> =
            dataStore.data.map { (it[Keys.TYPING_INDICATORS_ENABLED] ?: true) && it[Keys.SANCHR_MODE_ENABLED] != true }

        /**
         * Whether anything about presence may be sent at all. With online
         * status merely hidden a peer is still told "hidden"; under Sanchr
         * Mode they are told nothing, as on iOS.
         */
        val canSendPresence: Flow<Boolean> = dataStore.data.map { it[Keys.SANCHR_MODE_ENABLED] != true }

        /** Whether the window is shielded from capture: the toggle, or Sanchr Mode. */
        val screenSecurityActive: Flow<Boolean> =
            dataStore.data.map { (it[Keys.SCREENSHOT_PROTECTION_ENABLED] ?: true) || it[Keys.SANCHR_MODE_ENABLED] == true }

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

        /**
         * Who may see the account's profile photo: "everyone", "contacts" or
         * "nobody". A tri-state, not a boolean — collapsing it loses the
         * "contacts" case, which is the whole point of the setting.
         */
        val profilePhotoVisibility: Flow<String> =
            dataStore.data.map { it[Keys.PROFILE_PHOTO_VISIBILITY] ?: PROFILE_PHOTO_VISIBILITY_DEFAULT }

        suspend fun setProfilePhotoVisibility(visibility: String) {
            dataStore.edit { it[Keys.PROFILE_PHOTO_VISIBILITY] = visibility }
        }

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

        /** Fetch a card for links in chats (iOS `sanchr.linkPreviews`, on by default). The site sees this device's address. */
        val linkPreviewsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.LINK_PREVIEWS_ENABLED] ?: true }

        suspend fun setLinkPreviewsEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.LINK_PREVIEWS_ENABLED] = enabled }
        }

        // --- Security ---

        /**
         * App Lock. Held in the view model only until now, so the toggle was
         * forgotten on restart and nothing ever read it.
         */
        val screenLockEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.SCREEN_LOCK_ENABLED] ?: false }

        suspend fun setScreenLockEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.SCREEN_LOCK_ENABLED] = enabled }
        }

        /** Seconds in the background before the lock re-arms; 0 means immediately. */
        val screenLockTimeoutSeconds: Flow<Int> = dataStore.data.map { it[Keys.SCREEN_LOCK_TIMEOUT] ?: 0 }

        suspend fun setScreenLockTimeoutSeconds(seconds: Int) {
            dataStore.edit { it[Keys.SCREEN_LOCK_TIMEOUT] = seconds }
        }

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
         * On by default, as iOS: a messenger's window should not land in a
         * screenshot or the recents thumbnail until the user says otherwise.
         * Sensitive screens (OTP, recovery-key) still force FLAG_SECURE
         * unconditionally via `SecureScreen()` regardless of this preference.
         */
        val screenshotProtectionEnabled: Flow<Boolean> =
            dataStore.data.map { it[Keys.SCREENSHOT_PROTECTION_ENABLED] ?: true }

        suspend fun setScreenshotProtectionEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.SCREENSHOT_PROTECTION_ENABLED] = enabled }
        }

        // --- Media ---

        /**
         * Whether crash reports may leave the device.
         *
         * Off by default, deliberately. A crash report carries a stack trace
         * and device details to a third party, and this app's whole premise
         * is that nothing about a conversation goes anywhere the user did not
         * send it. Sending diagnostics is the user's call, not a default.
         */
        val crashReportingEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.CRASH_REPORTING_ENABLED] ?: false }

        suspend fun setCrashReportingEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.CRASH_REPORTING_ENABLED] = enabled }
        }

        /**
         * The account-wide chat wallpaper. A conversation with its own choice
         * overrides this; the Appearance picker used to be a no-op.
         */
        val chatWallpaper: Flow<String> = dataStore.data.map { it[Keys.CHAT_WALLPAPER] ?: "default" }

        suspend fun setChatWallpaper(name: String) {
            dataStore.edit { it[Keys.CHAT_WALLPAPER] = name }
        }

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

        /**
         * Whether the server's blocked list has been brought onto this
         * install. Once, not on every launch: after that the phone's own
         * record is the truth, and the server's copy can be the older one.
         */
        val blockListRestored: Flow<Boolean> = dataStore.data.map { it[Keys.BLOCK_LIST_RESTORED] ?: false }

        suspend fun setBlockListRestored(restored: Boolean) {
            dataStore.edit { it[Keys.BLOCK_LIST_RESTORED] = restored }
        }

        /**
         * Blocks and unblocks made on this phone that the server has not
         * been told about yet: user id to whether they are blocked.
         *
         * A block is written here first and sent afterwards. A send that
         * failed was only logged, so the server's list could stay wrong for
         * good — and the server's list is what a new phone starts from.
         */
        val pendingBlockSync: Flow<Map<String, Boolean>> =
            dataStore.data.map { prefs -> prefs[Keys.PENDING_BLOCK_SYNC].orEmpty().mapNotNull(::decodePendingBlock).toMap() }

        /** Records that [userId]'s state still has to reach the server, or with null that it has. */
        suspend fun setPendingBlockSync(
            userId: String,
            blocked: Boolean?,
        ) {
            dataStore.edit { prefs ->
                // The latest choice replaces an earlier one that never got sent.
                val others = prefs[Keys.PENDING_BLOCK_SYNC].orEmpty().filterNot { decodePendingBlock(it)?.first == userId }
                val updated = if (blocked == null) others else others + ((if (blocked) BLOCKED_MARK else UNBLOCKED_MARK) + userId)
                if (updated.isEmpty()) prefs.remove(Keys.PENDING_BLOCK_SYNC) else prefs[Keys.PENDING_BLOCK_SYNC] = updated.toSet()
            }
        }

        private fun decodePendingBlock(entry: String): Pair<String, Boolean>? =
            when {
                entry.length < 2 -> null
                entry.startsWith(BLOCKED_MARK) -> entry.drop(1) to true
                entry.startsWith(UNBLOCKED_MARK) -> entry.drop(1) to false
                else -> null
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

        companion object {
            private const val BLOCKED_MARK = "+"
            private const val UNBLOCKED_MARK = "-"

            /** Matches iOS's default and settings.proto's implicit empty-string case. */
            const val PROFILE_PHOTO_VISIBILITY_DEFAULT = "everyone"
        }
    }
