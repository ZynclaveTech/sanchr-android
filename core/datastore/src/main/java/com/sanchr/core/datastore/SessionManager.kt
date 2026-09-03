package com.sanchr.core.datastore

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages authentication tokens and session state using EncryptedSharedPreferences.
 * Tokens are stored encrypted at rest via Android Keystore-backed AES keys.
 */
@Singleton
class SessionManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private companion object {
            const val PREFS_NAME = "sanchr_session"
            const val KEY_ACCESS_TOKEN = "access_token"
            const val KEY_REFRESH_TOKEN = "refresh_token"
            const val KEY_USER_ID = "user_id"
            const val KEY_DEVICE_ID = "device_id"
            const val KEY_TOKEN_EXPIRY = "token_expiry"
            const val KEY_INSTALLATION_ID = "installation_id"
            const val KEY_DEVICE_MASTER_SECRET = "device_master_secret"
            const val KEY_RECOVERY_KEY = "recovery_key"
            const val KEY_BACKUP_ENABLED = "backup_enabled"
            const val KEY_BACKUP_LINEAGE_ID = "backup_lineage_id"
            const val KEY_BACKUP_FORMAT_VERSION = "backup_format_version"
            const val KEY_BACKUP_CONFIRMED_AT = "backup_confirmed_at"
            const val KEY_BACKUP_LAST_AT = "backup_last_at"
            const val KEY_BACKUP_LAST_CONTENT_HASH = "backup_last_content_hash"
            const val KEY_SENDER_CERTIFICATE = "sender_certificate_b64"
            const val KEY_ACCOUNT_PASSWORD = "account_password"
            const val KEY_ACCOUNT_PHONE_E164 = "account_phone_e164"
            const val KEY_DISPLAY_NAME = "display_name"
            const val KEY_FCM_TOKEN = "fcm_token"
        }

        private val masterKey: MasterKey by lazy {
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }

        private val encryptedPrefs: SharedPreferences by lazy {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        private val _isAuthenticated: MutableStateFlow<Boolean> by lazy {
            MutableStateFlow(getAccessToken() != null)
        }

        /** Observable authentication state. */
        val isAuthenticated: Flow<Boolean> get() = _isAuthenticated.asStateFlow()

        /** Observable session state as a [StateFlow] for collectors that need `.value` access. */
        val sessionActive: StateFlow<Boolean> get() = _isAuthenticated.asStateFlow()

        /** Returns the current access token, or null if not authenticated. */
        fun getAccessToken(): String? = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)

        /** Returns the current refresh token. */
        fun getRefreshToken(): String? = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)

        /** Returns the current user's ID. */
        fun getUserId(): String? = encryptedPrefs.getString(KEY_USER_ID, null)

        /** Returns the device ID used for push notifications and session tracking. */
        fun getDeviceId(): String? = encryptedPrefs.getString(KEY_DEVICE_ID, null)

        /** Returns the token expiry timestamp in epoch millis. */
        fun getTokenExpiry(): Long = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0L)

        /** Returns true if the current access token has expired. */
        fun isTokenExpired(): Boolean {
            val expiry = getTokenExpiry()
            return expiry > 0 && System.currentTimeMillis() >= expiry
        }

        /** Returns the stable per-installation identifier, creating it if missing. */
        fun getOrCreateInstallationId(): String {
            encryptedPrefs.getString(KEY_INSTALLATION_ID, null)?.takeIf { it.isNotBlank() }?.let {
                return it
            }

            val generated = UUID.randomUUID().toString().lowercase()
            encryptedPrefs
                .edit()
                .putString(KEY_INSTALLATION_ID, generated)
                .apply()
            return generated
        }

        fun getDeviceMasterSecret(): ByteArray? {
            val encoded = encryptedPrefs.getString(KEY_DEVICE_MASTER_SECRET, null) ?: return null
            return Base64.decode(encoded, Base64.NO_WRAP)
        }

        fun saveDeviceMasterSecret(secret: ByteArray) {
            encryptedPrefs
                .edit()
                .putString(KEY_DEVICE_MASTER_SECRET, Base64.encodeToString(secret, Base64.NO_WRAP))
                .apply()
        }

        fun getRecoveryKey(): String? = encryptedPrefs.getString(KEY_RECOVERY_KEY, null)

        fun saveRecoveryKey(recoveryKey: String) {
            encryptedPrefs
                .edit()
                .putString(KEY_RECOVERY_KEY, recoveryKey)
                .apply()
        }

        fun getBackupConfiguration(): BackupConfiguration? {
            if (!encryptedPrefs.getBoolean(KEY_BACKUP_ENABLED, false)) {
                return null
            }

            val lineageId = encryptedPrefs.getString(KEY_BACKUP_LINEAGE_ID, null) ?: return null
            val confirmedAt = encryptedPrefs.getLong(KEY_BACKUP_CONFIRMED_AT, 0L)
            if (confirmedAt <= 0L) {
                return null
            }

            val lastBackupAt = encryptedPrefs.getLong(KEY_BACKUP_LAST_AT, -1L)
            return BackupConfiguration(
                isEnabled = true,
                lineageId = lineageId,
                formatVersion = encryptedPrefs.getInt(KEY_BACKUP_FORMAT_VERSION, 1),
                recoveryKeyConfirmedAtMillis = confirmedAt,
                lastBackupAtMillis = lastBackupAt.takeIf { it > 0L },
                lastBackupContentHash = encryptedPrefs.getString(KEY_BACKUP_LAST_CONTENT_HASH, null),
            )
        }

        fun saveBackupConfiguration(configuration: BackupConfiguration) {
            encryptedPrefs
                .edit()
                .putBoolean(KEY_BACKUP_ENABLED, configuration.isEnabled)
                .putString(KEY_BACKUP_LINEAGE_ID, configuration.lineageId)
                .putInt(KEY_BACKUP_FORMAT_VERSION, configuration.formatVersion)
                .putLong(KEY_BACKUP_CONFIRMED_AT, configuration.recoveryKeyConfirmedAtMillis)
                .putLong(KEY_BACKUP_LAST_AT, configuration.lastBackupAtMillis ?: -1L)
                .putString(KEY_BACKUP_LAST_CONTENT_HASH, configuration.lastBackupContentHash)
                .apply()
        }

        /**
         * Returns the persisted sealed-sender certificate bytes, or null.
         *
         * Stored so [com.sanchr.core.crypto.sealed.SenderCertificateManager]
         * can re-hydrate its in-memory cache after a cold start without
         * round-tripping to the backend.
         */
        fun getSenderCertificate(): ByteArray? {
            val encoded = encryptedPrefs.getString(KEY_SENDER_CERTIFICATE, null) ?: return null
            return Base64.decode(encoded, Base64.NO_WRAP)
        }

        fun saveSenderCertificate(bytes: ByteArray) {
            encryptedPrefs
                .edit()
                .putString(KEY_SENDER_CERTIFICATE, Base64.encodeToString(bytes, Base64.NO_WRAP))
                .apply()
        }

        /**
         * Returns the randomly-generated per-account password issued at
         * Register time. Stored encrypted because the backend identifies the
         * device on subsequent `Login` calls via this secret (we never prompt
         * the user for it).
         */
        fun getAccountPassword(): String? = encryptedPrefs.getString(KEY_ACCOUNT_PASSWORD, null)

        fun saveAccountPassword(password: String) {
            encryptedPrefs
                .edit()
                .putString(KEY_ACCOUNT_PASSWORD, password)
                .apply()
        }

        /**
         * Returns the E.164 phone number associated with the active account, or
         * null when none is stored. Used by the fast-login bootstrap path and
         * OTP-resend to rebuild the Register payload without re-prompting the user.
         */
        fun getStoredPhoneE164(): String? = encryptedPrefs.getString(KEY_ACCOUNT_PHONE_E164, null)

        fun saveStoredPhoneE164(phoneE164: String) {
            encryptedPrefs
                .edit()
                .putString(KEY_ACCOUNT_PHONE_E164, phoneE164)
                .apply()
        }

        /** Display name, persisted locally after OTP verification. */
        fun getDisplayName(): String? = encryptedPrefs.getString(KEY_DISPLAY_NAME, null)

        fun saveDisplayName(displayName: String) {
            encryptedPrefs
                .edit()
                .putString(KEY_DISPLAY_NAME, displayName)
                .apply()
        }

        fun clearSenderCertificate() {
            encryptedPrefs.edit().remove(KEY_SENDER_CERTIFICATE).apply()
        }

        fun clearDeviceSecrets() {
            encryptedPrefs
                .edit()
                .remove(KEY_DEVICE_MASTER_SECRET)
                .apply()
        }

        fun clearBackupMaterial() {
            encryptedPrefs
                .edit()
                .remove(KEY_RECOVERY_KEY)
                .remove(KEY_BACKUP_ENABLED)
                .remove(KEY_BACKUP_LINEAGE_ID)
                .remove(KEY_BACKUP_FORMAT_VERSION)
                .remove(KEY_BACKUP_CONFIRMED_AT)
                .remove(KEY_BACKUP_LAST_AT)
                .remove(KEY_BACKUP_LAST_CONTENT_HASH)
                .apply()
        }

        /**
         * Saves a new session after successful authentication.
         */
        fun saveSession(
            accessToken: String,
            refreshToken: String,
            userId: String,
            expiresAtMillis: Long,
        ) {
            encryptedPrefs
                .edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_USER_ID, userId)
                .putLong(KEY_TOKEN_EXPIRY, expiresAtMillis)
                .apply()

            _isAuthenticated.value = true
        }

        /**
         * Updates only the access token (after a token refresh).
         */
        fun updateAccessToken(
            accessToken: String,
            expiresAtMillis: Long,
        ) {
            encryptedPrefs
                .edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putLong(KEY_TOKEN_EXPIRY, expiresAtMillis)
                .apply()
        }

        /**
         * Saves the device ID for push notification registration.
         */
        fun saveDeviceId(deviceId: String) {
            encryptedPrefs
                .edit()
                .putString(KEY_DEVICE_ID, deviceId)
                .apply()
        }

        /** Last FCM registration token the backend accepted; compared before re-uploading. */
        fun getFcmToken(): String? = encryptedPrefs.getString(KEY_FCM_TOKEN, null)

        fun saveFcmToken(token: String) {
            encryptedPrefs.edit().putString(KEY_FCM_TOKEN, token).apply()
        }

        /**
         * Clears all session data (logout).
         *
         * Uses `.commit()` (not `.apply()`) so the disk wipe is guaranteed to
         * have landed before we flip the in-memory auth flag. Prevents an
         * inconsistent state where a crash between the flag flip and the
         * async disk write would leave tokens on disk while `NavHost` believes
         * logout completed — the next cold start would then silently re-auth
         * the "logged-out" user. `.commit()` is synchronous and returns only
         * after fsync completes, making the ordering `disk wipe → RAM flip`
         * crash-safe. Review finding P1#2 (Phase 8).
         */
        fun clearSession() {
            encryptedPrefs
                .edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_DEVICE_ID)
                .remove(KEY_TOKEN_EXPIRY)
                .remove(KEY_INSTALLATION_ID)
                .remove(KEY_ACCOUNT_PASSWORD)
                .remove(KEY_ACCOUNT_PHONE_E164)
                .remove(KEY_DISPLAY_NAME)
                .remove(KEY_SENDER_CERTIFICATE)
                .remove(KEY_DEVICE_MASTER_SECRET)
                .remove(KEY_RECOVERY_KEY)
                .remove(KEY_BACKUP_ENABLED)
                .remove(KEY_BACKUP_LINEAGE_ID)
                .remove(KEY_BACKUP_FORMAT_VERSION)
                .remove(KEY_BACKUP_CONFIRMED_AT)
                .remove(KEY_BACKUP_LAST_AT)
                .remove(KEY_BACKUP_LAST_CONTENT_HASH)
                .remove(KEY_FCM_TOKEN)
                .commit()
            _isAuthenticated.value = false
        }
    }
