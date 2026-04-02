package com.sanchr.proto.messaging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StartDirectConversationRequest(
    @SerialName("recipient_user_id") val recipientUserId: String = "",
)

@Serializable
data class DeviceMessage(
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("registration_id") val registrationId: Int = 0,
    @SerialName("cipher_text") val cipherText: ByteArray = ByteArray(0),
    @SerialName("message_type") val messageType: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceMessage) return false
        return deviceId == other.deviceId &&
            registrationId == other.registrationId &&
            cipherText.contentEquals(other.cipherText) &&
            messageType == other.messageType
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + registrationId
        result = 31 * result + cipherText.contentHashCode()
        result = 31 * result + messageType
        return result
    }
}

@Serializable
data class SendMessageRequest(
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("recipient_user_id") val recipientUserId: String = "",
    @SerialName("content_type") val contentType: String = "text",
    val messages: List<DeviceMessage> = emptyList(),
    @SerialName("client_message_id") val clientMessageId: String = "",
    val timestamp: Long = 0L,
)

@Serializable
data class SendMessageResponse(
    @SerialName("server_message_id") val serverMessageId: String = "",
    val timestamp: Long = 0L,
    val success: Boolean = false,
)

@Serializable
sealed interface ClientEvent {

    @Serializable
    @SerialName("typing")
    data class Typing(
        @SerialName("conversation_id") val conversationId: String = "",
        @SerialName("is_typing") val isTyping: Boolean = false,
    ) : ClientEvent

    @Serializable
    @SerialName("receipt")
    data class Receipt(
        @SerialName("message_ids") val messageIds: List<String> = emptyList(),
        @SerialName("receipt_type") val receiptType: String = "delivered",
    ) : ClientEvent

    @Serializable
    @SerialName("heartbeat")
    data class Heartbeat(
        val timestamp: Long = 0L,
    ) : ClientEvent
}

@Serializable
sealed interface ServerEvent {

    @Serializable
    @SerialName("message")
    data class Message(
        val envelope: EncryptedEnvelope? = null,
    ) : ServerEvent

    @Serializable
    @SerialName("typing")
    data class Typing(
        val indicator: TypingIndicator? = null,
    ) : ServerEvent

    @Serializable
    @SerialName("receipt")
    data class Receipt(
        val update: ReceiptUpdate? = null,
    ) : ServerEvent

    @Serializable
    @SerialName("presence")
    data class Presence(
        val update: PresenceUpdate? = null,
    ) : ServerEvent

    @Serializable
    @SerialName("prekey_count_low")
    data class PreKeyCountLow(
        val info: com.sanchr.proto.messaging.PreKeyCountLow? = null,
    ) : ServerEvent
}

@Serializable
data class EncryptedEnvelope(
    @SerialName("source_user_id") val sourceUserId: String = "",
    @SerialName("source_device_id") val sourceDeviceId: String = "",
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("server_message_id") val serverMessageId: String = "",
    @SerialName("content_type") val contentType: String = "",
    @SerialName("cipher_text") val cipherText: ByteArray = ByteArray(0),
    @SerialName("message_type") val messageType: Int = 0,
    val timestamp: Long = 0L,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedEnvelope) return false
        return sourceUserId == other.sourceUserId &&
            serverMessageId == other.serverMessageId
    }

    override fun hashCode(): Int = serverMessageId.hashCode()
}

@Serializable
data class TypingIndicator(
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("is_typing") val isTyping: Boolean = false,
)

@Serializable
data class ReceiptRequest(
    @SerialName("message_ids") val messageIds: List<String> = emptyList(),
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("receipt_type") val receiptType: String = "delivered",
)

@Serializable
data class ReceiptResponse(
    val success: Boolean = false,
)

@Serializable
data class ReceiptUpdate(
    @SerialName("message_ids") val messageIds: List<String> = emptyList(),
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("receipt_type") val receiptType: String = "",
    val timestamp: Long = 0L,
)

@Serializable
data class PresenceHeartbeat(
    val status: String = "online",
    val timestamp: Long = 0L,
)

@Serializable
data class PresenceUpdate(
    @SerialName("user_id") val userId: String = "",
    val status: String = "",
    @SerialName("last_seen") val lastSeen: Long = 0L,
)

@Serializable
data class PreKeyCountLow(
    val remaining: Int = 0,
    val threshold: Int = 0,
)

@Serializable
data class SyncRequest(
    @SerialName("last_sync_timestamp") val lastSyncTimestamp: Long = 0L,
    val limit: Int = 100,
)

@Serializable
data class DeleteMessageRequest(
    @SerialName("message_id") val messageId: String = "",
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("for_everyone") val forEveryone: Boolean = false,
)

@Serializable
data class DeleteMessageResponse(
    val success: Boolean = false,
)

@Serializable
data class GetConversationsRequest(
    @SerialName("page_token") val pageToken: String = "",
    @SerialName("page_size") val pageSize: Int = 50,
)

@Serializable
data class GetConversationsResponse(
    val conversations: List<Conversation> = emptyList(),
    @SerialName("next_page_token") val nextPageToken: String = "",
)

@Serializable
data class Conversation(
    val id: String = "",
    val type: String = "direct",
    @SerialName("participant_ids") val participantIds: List<String> = emptyList(),
    val title: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("last_message_preview") val lastMessagePreview: String = "",
    @SerialName("last_message_timestamp") val lastMessageTimestamp: Long = 0L,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("is_muted") val isMuted: Boolean = false,
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("updated_at") val updatedAt: Long = 0L,
)
