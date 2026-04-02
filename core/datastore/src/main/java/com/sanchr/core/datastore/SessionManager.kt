package com.sanchr.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages authentication tokens and session state using EncryptedSharedPreferences.
 * Tokens are stored encrypted at rest via Android Keystore-backed AES keys.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val PREFS_NAME = "sanchr_session"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_TOKEN_EXPIRY = "token_expiry"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _isAuthenticated = MutableStateFlow(getAccessToken() != null)

    /** Observable authentication state. */
    val isAuthenticated: Flow<Boolean> = _isAuthenticated.asStateFlow()

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

    /**
     * Saves a new session after successful authentication.
     */
    fun saveSession(
        accessToken: String,
        refreshToken: String,
        userId: String,
        expiresAtMillis: Long,
    ) {
        encryptedPrefs.edit()
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
    fun updateAccessToken(accessToken: String, expiresAtMillis: Long) {
        encryptedPrefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_TOKEN_EXPIRY, expiresAtMillis)
            .apply()
    }

    /**
     * Saves the device ID for push notification registration.
     */
    fun saveDeviceId(deviceId: String) {
        encryptedPrefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .apply()
    }

    /**
     * Clears all session data (logout).
     */
    fun clearSession() {
        encryptedPrefs.edit().clear().apply()
        _isAuthenticated.value = false
    }
}
