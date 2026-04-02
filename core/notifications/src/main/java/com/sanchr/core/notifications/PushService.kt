package com.sanchr.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FCM push notification service.
 *
 * Handles two callbacks from Firebase Cloud Messaging:
 * 1. **onNewToken** -- called when the FCM registration token is created or rotated.
 *    The token is forwarded to the backend via [PushTokenManager].
 * 2. **onMessageReceived** -- called when a data-only push arrives (the backend never
 *    sends display notifications, only data payloads, so the app has full control).
 *
 * Notification types handled:
 * - `message` -- new encrypted chat message
 * - `call`    -- incoming voice/video call
 * - `system`  -- security alerts, app updates, contact-joined, key-change, etc.
 */
@AndroidEntryPoint
class SanchrPushService : FirebaseMessagingService() {

    @Inject lateinit var notificationHandler: NotificationHandler
    @Inject lateinit var tokenManager: PushTokenManager

    /**
     * Service-scoped coroutine scope. We use [SupervisorJob] so a single failure
     * does not cancel the entire scope, and [Dispatchers.IO] because token upload
     * performs network I/O.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ------------------------------------------------------------------
    // Token management
    // ------------------------------------------------------------------

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            tokenManager.uploadToken()
        }
    }

    // ------------------------------------------------------------------
    // Message handling
    // ------------------------------------------------------------------

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val payload = PushPayload.fromRemoteMessage(message)

        when (payload.type) {
            PushPayload.TYPE_MESSAGE -> handleMessagePayload(payload)
            PushPayload.TYPE_CALL -> handleCallPayload(payload)
            PushPayload.TYPE_SYSTEM -> handleSystemPayload(payload)
            else -> handleSystemPayload(payload)
        }
    }

    // ------------------------------------------------------------------
    // Payload handlers
    // ------------------------------------------------------------------

    private fun handleMessagePayload(payload: PushPayload) {
        notificationHandler.showMessageNotification(payload)

        // Update the summary/badge for grouped notifications
        payload.badge?.let { count ->
            notificationHandler.updateSummaryNotification(count)
        }
    }

    private fun handleCallPayload(payload: PushPayload) {
        notificationHandler.showCallNotification(payload)
    }

    private fun handleSystemPayload(payload: PushPayload) {
        val title = payload.title ?: "Sanchr"
        val body = payload.body ?: return
        notificationHandler.showSystemNotification(title, body)
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
