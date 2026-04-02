package com.sanchr.core.notifications

import com.google.firebase.messaging.RemoteMessage

/**
 * Typed representation of an incoming FCM push payload.
 *
 * The backend sends all fields as string key-value pairs in the data payload
 * (not the notification payload) so the app retains full control over display.
 */
data class PushPayload(
    val type: String,
    val conversationId: String?,
    val senderId: String?,
    val senderName: String?,
    val messagePreview: String?,
    val callId: String?,
    val callType: String?,
    val badge: Int?,
    val title: String?,
    val body: String?,
) {
    companion object {
        const val TYPE_MESSAGE = "message"
        const val TYPE_CALL = "call"
        const val TYPE_SYSTEM = "system"

        private const val KEY_TYPE = "type"
        private const val KEY_CONVERSATION_ID = "conversation_id"
        private const val KEY_SENDER_ID = "sender_id"
        private const val KEY_SENDER_NAME = "sender_name"
        private const val KEY_MESSAGE_PREVIEW = "message_preview"
        private const val KEY_CALL_ID = "call_id"
        private const val KEY_CALL_TYPE = "call_type"
        private const val KEY_BADGE = "badge"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"

        /**
         * Parses a [RemoteMessage] data map into a [PushPayload].
         *
         * If the message contains a notification body (sent from Firebase console, for example)
         * it is merged into the payload title/body fields as a fallback.
         */
        fun fromRemoteMessage(message: RemoteMessage): PushPayload {
            val data = message.data
            return PushPayload(
                type = data[KEY_TYPE] ?: TYPE_SYSTEM,
                conversationId = data[KEY_CONVERSATION_ID],
                senderId = data[KEY_SENDER_ID],
                senderName = data[KEY_SENDER_NAME],
                messagePreview = data[KEY_MESSAGE_PREVIEW],
                callId = data[KEY_CALL_ID],
                callType = data[KEY_CALL_TYPE],
                badge = data[KEY_BADGE]?.toIntOrNull(),
                title = data[KEY_TITLE] ?: message.notification?.title,
                body = data[KEY_BODY] ?: message.notification?.body,
            )
        }
    }
}
