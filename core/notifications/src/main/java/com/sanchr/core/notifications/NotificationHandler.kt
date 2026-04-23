package com.sanchr.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sanchr.core.database.entity.MessageEntity
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
class NotificationHandler
    @Inject
    constructor(
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
            val messageChannel =
                NotificationChannel(
                    CHANNEL_MESSAGES,
                    "Messages",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "New message notifications"
                    enableVibration(true)
                    enableLights(true)
                }

            val callChannel =
                NotificationChannel(
                    CHANNEL_CALLS,
                    "Calls",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Incoming call notifications"
                    enableVibration(true)
                    setSound(
                        null, // Default ringtone; users can override in system settings
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .build(),
                    )
                }

            val systemChannel =
                NotificationChannel(
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
         * Posts a notification for a freshly-decrypted inbound message.
         *
         * Called by [NewMessageNotifier] from the local DB flow — never
         * from FCM data. Honors the metadata-privacy rule from Phase C.5:
         * by default the notification uses [NotificationCompat.VISIBILITY_PRIVATE]
         * plus a generic public version so nothing sensitive shows on the
         * lockscreen; the unlocked/expanded version carries the sender and
         * preview.
         *
         * @param entity the decrypted message row from Room.
         * @param senderDisplayName resolved externally (ContactDao /
         *   participant lookup) — can be null when unknown, in which case
         *   the notification falls back to "New message".
         * @param showPreviewOnLockscreen when true, upgrades visibility to
         *   [NotificationCompat.VISIBILITY_PUBLIC] so the unlocked preview
         *   is also rendered on the lockscreen. Defaults to false; the
         *   UserPreferences wiring lives in the notifier (C.5).
         */
        fun showMessageNotification(
            entity: MessageEntity,
            senderDisplayName: String?,
            showPreviewOnLockscreen: Boolean = false,
        ) {
            val notificationId = entity.conversationId.hashCode()
            val contentPendingIntent =
                PendingIntent.getActivity(
                    context,
                    notificationId,
                    buildDeepLinkIntent(entity.conversationId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val displayTitle = senderDisplayName?.takeIf { it.isNotBlank() } ?: "New message"
            val preview =
                if (entity.contentType.equals("text", ignoreCase = true)) {
                    entity.contentBody
                } else {
                    // Non-text content types get a generic label; do not leak
                    // binary blobs or URLs into the system notification.
                    entity.contentType.lowercase().replaceFirstChar { it.uppercase() }
                }

            val publicVersion =
                NotificationCompat
                    .Builder(context, CHANNEL_MESSAGES)
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle("New message")
                    .setContentText("")
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setGroup(NOTIFICATION_GROUP_MESSAGES)
                    .build()

            val visibility =
                if (showPreviewOnLockscreen) {
                    NotificationCompat.VISIBILITY_PUBLIC
                } else {
                    NotificationCompat.VISIBILITY_PRIVATE
                }

            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_MESSAGES)
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle(displayTitle)
                    .setContentText(preview)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
                    .setAutoCancel(true)
                    .setGroup(NOTIFICATION_GROUP_MESSAGES)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setContentIntent(contentPendingIntent)
                    .setVisibility(visibility)
                    .setPublicVersion(publicVersion)
                    .setWhen(entity.timestamp)
                    .build()

            notifyIfAllowed(notificationId, notification)
        }

        // -----------------------------------------------------------------------
        // System notifications
        // -----------------------------------------------------------------------

        /**
         * Posts a low-priority system notification (e.g., security alerts, app updates).
         */
        fun showSystemNotification(
            title: String,
            body: String,
        ) {
            val notificationId = (title + body).hashCode()

            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_SYSTEM)
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

            val summary =
                NotificationCompat
                    .Builder(context, CHANNEL_MESSAGES)
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
        private fun notifyIfAllowed(
            id: Int,
            notification: android.app.Notification,
        ) {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            try {
                manager.notify(id, notification)
            } catch (_: SecurityException) {
                // Permission revoked between check and call; swallow.
            }
        }

        private fun buildDeepLinkIntent(conversationId: String): Intent =
            Intent(context, Class.forName("com.sanchr.app.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("conversationId", conversationId)
            }
    }
