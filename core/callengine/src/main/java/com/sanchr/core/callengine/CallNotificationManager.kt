package com.sanchr.core.callengine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallNotificationManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            const val CHANNEL_ID_CALL = "sanchr_call_channel"
            const val CHANNEL_ID_INCOMING = "sanchr_incoming_call_channel"
            const val NOTIFICATION_ID_ONGOING = 2001
            const val NOTIFICATION_ID_INCOMING = 2002

            const val ACTION_ANSWER = "com.sanchr.action.ANSWER_CALL"
            const val ACTION_DECLINE = "com.sanchr.action.DECLINE_CALL"
            const val ACTION_END_CALL = "com.sanchr.action.END_CALL"
            const val ACTION_OPEN_CALL = "com.sanchr.action.OPEN_CALL"

            const val EXTRA_CALL_ID = "extra_call_id"
        }

        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        init {
            createNotificationChannels()
        }

        private fun createNotificationChannels() {
            // Ongoing call channel
            val ongoingChannel =
                NotificationChannel(
                    CHANNEL_ID_CALL,
                    "Ongoing Calls",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Notification shown during active calls"
                    setShowBadge(false)
                }
            notificationManager.createNotificationChannel(ongoingChannel)

            // Incoming call channel (high importance for heads-up)
            val incomingChannel =
                NotificationChannel(
                    CHANNEL_ID_INCOMING,
                    "Incoming Calls",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Incoming call notifications"
                    setShowBadge(true)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            notificationManager.createNotificationChannel(incomingChannel)
        }

        fun showOngoingCallNotification(
            callerName: String,
            callType: String,
            isIncoming: Boolean,
        ): Notification {
            val openIntent =
                Intent(ACTION_OPEN_CALL).apply {
                    setPackage(context.packageName)
                }
            val openPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val endIntent =
                Intent(context, CallService::class.java).apply {
                    action = CallService.ACTION_END_CALL
                }
            val endPendingIntent =
                PendingIntent.getService(
                    context,
                    1,
                    endIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val typeLabel = if (callType == "video") "Video call" else "Voice call"
            val statusLabel = if (isIncoming) "Incoming $typeLabel" else "Ongoing $typeLabel"

            return NotificationCompat
                .Builder(context, CHANNEL_ID_CALL)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle(callerName)
                .setContentText(statusLabel)
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "End",
                    endPendingIntent,
                ).setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }

        fun showIncomingCallNotification(
            callerName: String,
            isVideo: Boolean,
            callId: String = "",
        ): Notification {
            val answerIntent =
                Intent(context, CallService::class.java).apply {
                    action = CallService.ACTION_ANSWER
                    putExtra(EXTRA_CALL_ID, callId)
                }
            val answerPendingIntent =
                PendingIntent.getService(
                    context,
                    10,
                    answerIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val declineIntent =
                Intent(context, CallService::class.java).apply {
                    action = CallService.ACTION_DECLINE
                    putExtra(EXTRA_CALL_ID, callId)
                }
            val declinePendingIntent =
                PendingIntent.getService(
                    context,
                    11,
                    declineIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val fullScreenIntent =
                Intent(ACTION_OPEN_CALL).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_CALL_ID, callId)
                }
            val fullScreenPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    12,
                    fullScreenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val callTypeLabel = if (isVideo) "Incoming video call" else "Incoming voice call"

            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID_INCOMING)
                    .setSmallIcon(android.R.drawable.ic_menu_call)
                    .setContentTitle(callerName)
                    .setContentText(callTypeLabel)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Decline",
                        declinePendingIntent,
                    ).addAction(
                        android.R.drawable.ic_menu_call,
                        "Answer",
                        answerPendingIntent,
                    ).setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                    .build()

            notificationManager.notify(NOTIFICATION_ID_INCOMING, notification)
            return notification
        }

        fun cancelCallNotification() {
            notificationManager.cancel(NOTIFICATION_ID_ONGOING)
            notificationManager.cancel(NOTIFICATION_ID_INCOMING)
        }
    }
