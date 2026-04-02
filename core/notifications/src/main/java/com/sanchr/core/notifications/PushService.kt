package com.sanchr.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.sanchr.core.datastore.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// TODO: Extend FirebaseMessagingService once Firebase is configured.
// For now, this is a standalone service skeleton.

/**
 * Handles incoming FCM push notifications.
 *
 * Notification types:
 *   - "message": New encrypted message received
 *   - "call": Incoming voice/video call
 *   - "contact_joined": A contact joined Sanchr
 *   - "key_change": Remote user's identity key changed
 */
@AndroidEntryPoint
class PushService : android.app.Service() {

    @Inject
    lateinit var sessionManager: SessionManager

    companion object {
        const val CHANNEL_MESSAGES = "sanchr_messages"
        const val CHANNEL_CALLS = "sanchr_calls"
        const val CHANNEL_SYSTEM = "sanchr_system"

        private const val NOTIFICATION_GROUP_MESSAGES = "com.sanchr.MESSAGES"
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /**
     * Called when a new FCM token is generated. Upload to server for targeting.
     */
    fun onNewToken(token: String) {
        sessionManager.saveDeviceId(token)
        // TODO: Upload token to backend via gRPC
    }

    /**
     * Called when a push notification is received.
     */
    fun onMessageReceived(data: Map<String, String>) {
        val type = data["type"] ?: return

        when (type) {
            "message" -> handleMessageNotification(data)
            "call" -> handleCallNotification(data)
            "contact_joined" -> handleContactJoinedNotification(data)
            "key_change" -> handleKeyChangeNotification(data)
        }
    }

    private fun handleMessageNotification(data: Map<String, String>) {
        val senderId = data["sender_id"] ?: return
        val conversationId = data["conversation_id"] ?: return

        // TODO: Decrypt the notification payload using Signal protocol
        // TODO: Show notification with sender name and message preview
        // TODO: Respect user's notification preview settings

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email) // TODO: Use app icon
            .setContentTitle("New message") // TODO: Replace with sender name
            .setContentText("You have a new encrypted message")
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_GROUP_MESSAGES)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            // TODO: Add reply action, mark-as-read action
            // TODO: Add deep link PendingIntent to conversation
            .build()

        notificationManager.notify(conversationId.hashCode(), notification)
    }

    private fun handleCallNotification(data: Map<String, String>) {
        // TODO: Show full-screen incoming call notification
        // TODO: Use CHANNEL_CALLS with high importance
        // TODO: Start foreground service for call
    }

    private fun handleContactJoinedNotification(data: Map<String, String>) {
        // TODO: Show a low-priority notification that a contact joined
    }

    private fun handleKeyChangeNotification(data: Map<String, String>) {
        // TODO: Show security-sensitive notification about key change
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
            NotificationManager.IMPORTANCE_MAX,
        ).apply {
            description = "Incoming call notifications"
            enableVibration(true)
            setSound(
                null, // TODO: Set custom ringtone URI
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
            description = "System notifications"
        }

        notificationManager.createNotificationChannels(
            listOf(messageChannel, callChannel, systemChannel),
        )
    }
}
