package com.sanchr.core.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.notifications.NotificationServiceClient
import com.sanchr.proto.notifications.RegisterPushTokenRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Manages the FCM push token lifecycle.
 *
 * Responsibilities:
 * - Retrieve the current FCM registration token
 * - Upload tokens to the backend via [NotificationServiceClient.registerPushToken]
 * - Refresh and re-upload after token rotation or user login
 * - Clear the persisted token on logout
 */
@Singleton
class PushTokenManager
    @Inject
    constructor(
        private val notificationClient: NotificationServiceClient,
        private val sessionManager: SessionManager,
        private val tokenSource: FcmTokenSource,
    ) {
        companion object {
            private const val TAG = "PushTokenManager"
        }

        /**
         * Returns the current FCM registration token, or null if unavailable.
         */
        suspend fun getCurrentToken(): String? =
            try {
                tokenSource.currentToken()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to retrieve FCM token", e)
                null
            }

        /**
         * Uploads the current FCM token to the Sanchr backend.
         * No-ops silently if the token cannot be retrieved, the user is not authenticated,
         * or the token has already been uploaded.
         */
        suspend fun uploadToken() {
            val token =
                getCurrentToken() ?: run {
                    Log.w(TAG, "No FCM token available; skipping upload")
                    return
                }

            if (sessionManager.getAccessToken() == null) {
                Log.d(TAG, "User is not authenticated; deferring token upload")
                return
            }

            if (sessionManager.getFcmToken() == token) return

            try {
                notificationClient.registerPushToken(
                    RegisterPushTokenRequest(
                        token = token,
                        platform = "android",
                    ),
                )
                // The server device id (SessionManager.getDeviceId) is the numeric id from
                // AuthResponse and feeds x-device-id and the key bundle. It used to be
                // overwritten with this token, which uploaded key bundles for device 0.
                sessionManager.saveFcmToken(token)
                Log.d(TAG, "FCM token uploaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload FCM token to backend", e)
            }
        }

        /**
         * Refreshes the FCM token and uploads it to the backend.
         * Called on new-token callbacks and after login.
         */
        suspend fun refreshAndUpload() {
            try {
                // Delete the old token to force a new one
                FirebaseMessaging.getInstance().deleteToken().await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete old FCM token", e)
            }
            uploadToken()
        }

        /**
         * Clears the persisted token. Called on logout so a stale token is not re-used.
         */
        suspend fun clearToken() {
            try {
                FirebaseMessaging.getInstance().deleteToken().await()
                Log.d(TAG, "FCM token deleted")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete FCM token on logout", e)
            }
        }
    }
