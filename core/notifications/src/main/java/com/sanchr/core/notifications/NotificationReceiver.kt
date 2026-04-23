package com.sanchr.core.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

/**
 * Handles notification action buttons:
 * - Inline reply to a message
 * - Mark conversation as read
 * - Answer an incoming call
 * - Decline an incoming call
 *
 * Because [BroadcastReceiver] cannot use constructor injection with Hilt
 * (it is not a Hilt entry point), the receiver uses [goAsync] and delegates
 * actual work to the application's service layer via explicit intents or
 * direct repository calls obtained from the application context.
 */
class NotificationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REPLY = "com.sanchr.action.REPLY"
        const val ACTION_MARK_READ = "com.sanchr.action.MARK_READ"
        const val ACTION_ANSWER_CALL = "com.sanchr.action.ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.sanchr.action.DECLINE_CALL"

        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_REPLY -> handleReply(context, intent)
            ACTION_MARK_READ -> handleMarkRead(context, intent)
            ACTION_ANSWER_CALL -> handleAnswerCall(context, intent)
            ACTION_DECLINE_CALL -> handleDeclineCall(context, intent)
        }
    }

    // ------------------------------------------------------------------
    // Reply
    // ------------------------------------------------------------------

    /**
     * Extracts inline-reply text from [RemoteInput] and forwards it for sending.
     *
     * After extracting the text, the notification is updated to show a "Sending..."
     * confirmation and then dismissed once the message is enqueued.
     */
    private fun handleReply(
        context: Context,
        intent: Intent,
    ) {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val remoteInputBundle = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = remoteInputBundle.getCharSequence(NotificationHandler.KEY_TEXT_REPLY)?.toString()
        if (replyText.isNullOrBlank()) return

        // Forward the reply to the messaging layer.
        // We broadcast an explicit intent that the app module can receive via a Hilt-injected
        // component, or alternatively use WorkManager for guaranteed delivery.
        val sendIntent =
            Intent("com.sanchr.action.SEND_REPLY").apply {
                setPackage(context.packageName)
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
                putExtra("reply_text", replyText)
            }
        context.sendBroadcast(sendIntent)

        // Dismiss the notification after the reply is enqueued
        if (notificationId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }
    }

    // ------------------------------------------------------------------
    // Mark as read
    // ------------------------------------------------------------------

    private fun handleMarkRead(
        context: Context,
        intent: Intent,
    ) {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        // Forward to the messaging layer to mark the conversation as read
        val markReadIntent =
            Intent("com.sanchr.action.MARK_CONVERSATION_READ").apply {
                setPackage(context.packageName)
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
            }
        context.sendBroadcast(markReadIntent)

        // Dismiss the notification
        if (notificationId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }
    }

    // ------------------------------------------------------------------
    // Call actions
    // ------------------------------------------------------------------

    /**
     * Handles the "Answer" action on an incoming call notification.
     * Launches the main activity with call extras so the call UI is shown.
     */
    private fun handleAnswerCall(
        context: Context,
        intent: Intent,
    ) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "voice"
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val launchIntent =
            Intent(context, Class.forName("com.sanchr.app.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("call_id", callId)
                putExtra("call_type", callType)
                putExtra("call_action", "answer")
            }
        context.startActivity(launchIntent)

        if (notificationId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }
    }

    /**
     * Handles the "Decline" action on an incoming call notification.
     * Sends a decline signal to the backend and dismisses the notification.
     */
    private fun handleDeclineCall(
        context: Context,
        intent: Intent,
    ) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val declineIntent =
            Intent("com.sanchr.action.DECLINE_CALL").apply {
                setPackage(context.packageName)
                putExtra(EXTRA_CALL_ID, callId)
            }
        context.sendBroadcast(declineIntent)

        if (notificationId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
        }
    }
}
