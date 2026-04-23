package com.sanchr.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central notification display manager.
 *
 * Responsible for creating Android notification channels, building and posting
 * system notifications, and managing notification lifecycle (cancel, summary, badge).
 *
 * Uses [NotificationManagerCompat] for backward-compatible posting.
 */
@Singleton
class NotificationHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_MESSAGES = "sanchr_messages"
        const val CHANNEL_CALLS = "sanchr_calls"
        const val CHANNEL_SYSTEM = "sanchr_system"

        private const val NOTIFICATION_GROUP_MESSAGES = "com.sanchr.MESSAGES"
        private const val SUMMARY_NOTIFICATION_ID = 0

        // RemoteInput key used by the inline-reply action
        const val KEY_TEXT_REPLY = "key_text_reply"
    }

    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // -----------------------------------------------------------------------
    // Channel creation
    // -----------------------------------------------------------------------

    /**
     * Creates all notification channels required by the app.
     * Safe to call multiple times -- the system ignores duplicate channel creation.
     * Should be called once during [android.app.Application.onCreate].
     */
    fun createNotificationChannels() {
        val messageChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "New message notifications"
            enableVibration(true)
            enableLights(true)
        }

        val callChannel = NotificationChannel(
            CHANNEL_CALLS,
            "Calls",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Incoming call notifications"
            enableVibration(true)
            setSound(
                null, // Default ringtone; users can override in system settings
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build(),
            )
        }

        val systemChannel = NotificationChannel(
            CHANNEL_SYSTEM,
            "System",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "System notifications (updates, security alerts)"
        }

        notificationManager.createNotificationChannels(
            listOf(messageChannel, callChannel, systemChannel),
        )
    }

    // -----------------------------------------------------------------------
    // Message notifications
    // -----------------------------------------------------------------------

    /**
     * Posts a notification for an incoming chat message.
     *
     * Includes:
     * - Inline reply action via [RemoteInput]
     * - Mark-as-read action
     * - Deep-link content intent into the conversation
     * - Grouped under [NOTIFICATION_GROUP_MESSAGES] for automatic bundling
     */
    fun showMessageNotification(payload: PushPayload) {
        val conversationId = payload.conversationId ?: return
        val notificationId = conversationId.hashCode()

        // Deep-link intent: opens MainActivity with extras for navigation
        val contentIntent = buildDeepLinkIntent(conversationId)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Inline reply action
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply")
            .build()

        val replyIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_REPLY
            putExtra(NotificationReceiver.EXTRA_CONVERSATION_ID, conversationId)
            putExtra(NotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        // Mark-as-read action
        val markReadIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_MARK_READ
            putExtra(NotificationReceiver.EXTRA_CONVERSATION_ID, conversationId)
            putExtra(NotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Mark as read",
            markReadPendingIntent,
        ).build()

        // Sender as Person for MessagingStyle
        val sender = Person.Builder()
            .setName(payload.senderName ?: "Unknown")
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(sender)
            .setConversationTitle(payload.senderName)
            .addMessage(
                payload.messagePreview ?: payload.body ?: "New message",
                System.currentTimeMillis(),
                sender,
            )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email) // TODO: Replace with R.drawable.ic_notification
            .setStyle(messagingStyle)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_GROUP_MESSAGES)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(contentPendingIntent)
            .addAction(replyAction)
            .addAction(markReadAction)
            .build()

        notifyIfAllowed(notificationId, notification)
    }

    // -----------------------------------------------------------------------
    // Call notifications
    // -----------------------------------------------------------------------

    /**
     * Posts a high-priority full-screen notification for an incoming call.
     *
     * On Android 10+ this will launch the full-screen intent when the device is locked.
     * On lower versions it behaves as a heads-up notification.
     */
    fun showCallNotification(payload: PushPayload) {
        val callId = payload.callId ?: return
        val notificationId = callId.hashCode()

        val callerName = payload.senderName ?: "Incoming call"
        val callTypeLabel = when (payload.callType) {
            "video" -> "Incoming video call"
            else -> "Incoming voice call"
        }

        // Full-screen intent -- opens MainActivity with call extras
        val fullScreenIntent = Intent(context, Class.forName("com.sanchr.app.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id", callId)
            putExtra("call_type", payload.callType)
            putExtra("caller_name", callerName)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Answer action
        val answerIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_ANSWER_CALL
            putExtra(NotificationReceiver.EXTRA_CALL_ID, callId)
            putExtra(NotificationReceiver.EXTRA_CALL_TYPE, payload.callType)
            putExtra(NotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 10,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Decline action
        val declineIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_DECLINE_CALL
            putExtra(NotificationReceiver.EXTRA_CALL_ID, callId)
            putExtra(NotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 11,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call) // TODO: Replace with app icon
            .setContentTitle(callerName)
            .setContentText(callTypeLabel)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Answer", answerPendingIntent)
            .build()

        notifyIfAllowed(notificationId, notification)
    }

    // -----------------------------------------------------------------------
    // System notifications
    // -----------------------------------------------------------------------

    /**
     * Posts a low-priority system notification (e.g., security alerts, app updates).
     */
    fun showSystemNotification(title: String, body: String) {
        val notificationId = (title + body).hashCode()

        val notification = NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Replace with app icon
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        notifyIfAllowed(notificationId, notification)
    }

    // -----------------------------------------------------------------------
    // Notification management
    // -----------------------------------------------------------------------

    /**
     * Cancels all notifications associated with a given conversation.
     * Called when the user opens that conversation.
     */
    fun cancelNotificationsForConversation(conversationId: String) {
        notificationManager.cancel(conversationId.hashCode())
    }

    /**
     * Cancels every notification posted by the app.
     */
    fun cancelAll() {
        notificationManager.cancelAll()
    }

    /**
     * Updates the summary / group notification with the current unread count.
     * On Android 7+ the system can auto-bundle grouped notifications, but we post
     * an explicit summary so we control the text.
     */
    fun updateSummaryNotification(unreadCount: Int) {
        if (unreadCount <= 0) {
            notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
            return
        }

        val summary = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Sanchr")
            .setContentText("$unreadCount unread messages")
            .setGroup(NOTIFICATION_GROUP_MESSAGES)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notifyIfAllowed(SUMMARY_NOTIFICATION_ID, summary)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Posts a notification only if the user has not revoked the POST_NOTIFICATIONS
     * runtime permission (Android 13+). Swallows [SecurityException] in the rare
     * race where permission is revoked between the check and the call.
     */
    private fun notifyIfAllowed(id: Int, notification: android.app.Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between check and call; swallow.
        }
    }

    private fun buildDeepLinkIntent(conversationId: String): Intent {
        return Intent(context, Class.forName("com.sanchr.app.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conversationId", conversationId)
        }
    }
}
