package com.sanchr.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

        val payload = PushPayload.fromData(message.data)
        if (payload == null) {
            android.util.Log.w("SanchrPushService", "ignoring push with no type field")
            return
        }
        // Phase C.2 rewires this to enqueue MessageDrainWorker. For now the
        // shrunk payload simply surfaces the wake signal without any content
        // — the old handleMessage/handleCall/handleSystem branches have been
        // removed along with their PushPayload fields.
        android.util.Log.d("SanchrPushService", "wake push received type=${payload.type}")
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
