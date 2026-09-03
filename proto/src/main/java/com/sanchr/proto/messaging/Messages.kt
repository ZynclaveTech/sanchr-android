package com.sanchr.proto.messaging

data class StartDirectConversationRequest(
    val recipientId: String = "",
)

data class DeviceMessage(
    val recipientId: String = "",
    val deviceId: Int = 0,
    val cipherText: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceMessage) return false
        return recipientId == other.recipientId &&
            deviceId == other.deviceId &&
            cipherText.contentEquals(other.cipherText)
    }

    override fun hashCode(): Int {
        var result = recipientId.hashCode()
        result = 31 * result + deviceId
        result = 31 * result + cipherText.contentHashCode()
        return result
    }
}

data class SendMessageRequest(
    val conversationId: String = "",
    val deviceMessages: List<DeviceMessage> = emptyList(),
    val contentType: String = "text",
    val expiresAfterSecs: Long = 0L,
)

data class SendMessageResponse(
    val messageId: String = "",
    val serverTimestamp: Long = 0L,
)

sealed interface ClientEvent {
    data class Typing(
        val conversationId: String = "",
        val userId: String = "",
        val isTyping: Boolean = false,
    ) : ClientEvent
}

sealed interface ServerEvent {
    data class Message(
        val envelope: EncryptedEnvelope? = null,
    ) : ServerEvent

    data class Typing(
        val indicator: TypingIndicator? = null,
    ) : ServerEvent

    data class Receipt(
        val update: ReceiptUpdate? = null,
    ) : ServerEvent

    data class PreKeyCountLow(
        val info: com.sanchr.proto.messaging.PreKeyCountLow? = null,
    ) : ServerEvent

    data class CallOffer(
        val offer: CallOfferEvent? = null,
    ) : ServerEvent

    data class CallLifecycle(
        val event: CallLifecycleEvent? = null,
    ) : ServerEvent
}

data class EncryptedEnvelope(
    val conversationId: String = "",
    val messageId: String = "",
    val senderId: String = "",
    val senderDevice: Int = 0,
    val cipherText: ByteArray = ByteArray(0),
    val contentType: String = "",
    val serverTimestamp: Long = 0L,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedEnvelope) return false
        return messageId == other.messageId
    }

    override fun hashCode(): Int = messageId.hashCode()
}

data class TypingIndicator(
    val conversationId: String = "",
    val userId: String = "",
    val isTyping: Boolean = false,
)

data class ReceiptRequest(
    val conversationId: String = "",
    val messageId: String = "",
    val status: String = "delivered",
)

data class ReceiptResponse(
    val success: Boolean = true,
)

data class ReceiptUpdate(
    val conversationId: String = "",
    val messageId: String = "",
    val recipientId: String = "",
    val status: String = "",
    val timestamp: Long = 0L,
)

enum class PresenceStatus {
    PRESENCE_STATUS_UNSPECIFIED,
    ONLINE,
    OFFLINE,
    HIDDEN,
}

enum class DevicePresenceState {
    DEVICE_STATE_UNSPECIFIED,
    FOREGROUND,
    BACKGROUND,
    OFFLINE_DEVICE,
}

data class PresenceHeartbeat(
    val deviceState: DevicePresenceState = DevicePresenceState.DEVICE_STATE_UNSPECIFIED,
    val sentAtMs: Long = 0L,
)

data class PresenceUpdate(
    val userId: String = "",
    val status: String = "",
    val lastSeen: Long = 0L,
    val statusCode: PresenceStatus = PresenceStatus.PRESENCE_STATUS_UNSPECIFIED,
)

data class PreKeyCountLow(
    val deviceId: Int = 0,
    val remainingCount: Int = 0,
)

data class CallOfferEvent(
    val callId: String = "",
    val callerId: String = "",
    val callType: String = "",
    val sdpOffer: ByteArray = ByteArray(0),
    val srtpKeyParams: ByteArray = ByteArray(0),
)

data class CallLifecycleEvent(
    val callId: String = "",
    val peerId: String = "",
    val eventType: String = "",
    val actorId: String = "",
)

data class SyncRequest(
    val sinceTimestamp: Long = 0L,
)

data class AckedMessageRef(
    val conversationId: String = "",
    val messageId: String = "",
)

data class AckMessagesRequest(
    val messages: List<AckedMessageRef> = emptyList(),
)

data class AckMessagesResponse(
    val success: Boolean = true,
)

data class DeleteMessageRequest(
    val conversationId: String = "",
    val messageId: String = "",
    val forEveryone: Boolean = false,
)

data class DeleteMessageResponse(
    val success: Boolean = true,
)

data class GetConversationsRequest(
    val pageSize: Int = 50,
)

data class GetConversationsResponse(
    val conversations: List<Conversation> = emptyList(),
)

data class Conversation(
    val id: String = "",
    val type: String = "direct",
    val participantIds: List<String> = emptyList(),
    val unreadCount: Int = 0,
    val participants: List<Participant> = emptyList(),
    val title: String = "",
    val avatarUrl: String = "",
    val lastMessagePreview: String = "",
    val lastMessageTimestamp: Long = 0L,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class Participant(
    val userId: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
)

data class SenderCertificateRequest(
    val placeholder: Unit = Unit,
)

data class SenderCertificateResponse(
    val certificate: ByteArray = ByteArray(0),
    val expiration: Long = 0L,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SenderCertificateResponse) return false
        return certificate.contentEquals(other.certificate) && expiration == other.expiration
    }

    override fun hashCode(): Int = certificate.contentHashCode() * 31 + expiration.hashCode()
}
