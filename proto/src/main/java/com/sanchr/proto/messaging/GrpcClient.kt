package com.sanchr.proto.messaging

import android.util.Log
import com.google.protobuf.ByteString
import io.grpc.CallOptions
import io.grpc.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import sanchr.messaging.Messaging
import sanchr.messaging.MessagingServiceGrpcKt

private const val TAG = "MessagingGrpcClient"

interface MessagingServiceClient {
    suspend fun sendMessage(request: SendMessageRequest): SendMessageResponse

    suspend fun startDirectConversation(request: StartDirectConversationRequest): Conversation

    fun messageStream(requests: Flow<ClientEvent>): Flow<ServerEvent>

    fun syncMessages(request: SyncRequest): Flow<EncryptedEnvelope>

    suspend fun ackMessages(request: AckMessagesRequest): AckMessagesResponse

    suspend fun deleteMessage(request: DeleteMessageRequest): DeleteMessageResponse

    suspend fun sendReceipt(request: ReceiptRequest): ReceiptResponse

    suspend fun getConversations(request: GetConversationsRequest): GetConversationsResponse

    suspend fun getSenderCertificate(request: SenderCertificateRequest): SenderCertificateResponse

    suspend fun getDeliveryTokens(request: DeliveryTokenRequest): DeliveryTokenResponse

    suspend fun sendSealedMessage(request: SendSealedMessageRequest): SendSealedMessageResponse

    suspend fun sendReaction(request: Reaction): Reaction

    suspend fun deleteConversation(request: DeleteConversationRequest): DeleteConversationResponse
}

class MessagingServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : MessagingServiceClient {
    private val stub = MessagingServiceGrpcKt.MessagingServiceCoroutineStub(channel, callOptions)

    override suspend fun sendMessage(request: SendMessageRequest): SendMessageResponse = stub.sendMessage(request.toProto()).toManual()

    override suspend fun startDirectConversation(request: StartDirectConversationRequest): Conversation =
        stub.startDirectConversation(request.toProto()).toManual()

    override fun messageStream(requests: Flow<ClientEvent>): Flow<ServerEvent> =
        stub.messageStream(requests.map(ClientEvent::toProto)).mapNotNull(Messaging.ServerEvent::toManual)

    override fun syncMessages(request: SyncRequest): Flow<EncryptedEnvelope> =
        stub.syncMessages(request.toProto()).map(Messaging.EncryptedEnvelope::toManual)

    override suspend fun ackMessages(request: AckMessagesRequest): AckMessagesResponse {
        stub.ackMessages(request.toProto())
        return AckMessagesResponse()
    }

    override suspend fun deleteMessage(request: DeleteMessageRequest): DeleteMessageResponse {
        stub.deleteMessage(request.toProto())
        return DeleteMessageResponse()
    }

    override suspend fun sendReceipt(request: ReceiptRequest): ReceiptResponse {
        stub.sendReceipt(request.toProto())
        return ReceiptResponse()
    }

    override suspend fun getConversations(request: GetConversationsRequest): GetConversationsResponse =
        stub.getConversations(Messaging.GetConversationsRequest.getDefaultInstance()).toManual()

    override suspend fun getSenderCertificate(request: SenderCertificateRequest): SenderCertificateResponse {
        val response =
            stub.getSenderCertificate(Messaging.SenderCertificateRequest.getDefaultInstance())
        return SenderCertificateResponse(
            certificate = response.certificate.toByteArray(),
            expiration = response.expiration,
        )
    }

    override suspend fun getDeliveryTokens(request: DeliveryTokenRequest): DeliveryTokenResponse =
        stub.getDeliveryTokens(request.toProto()).toManual()

    override suspend fun sendSealedMessage(request: SendSealedMessageRequest): SendSealedMessageResponse =
        stub.sendSealedMessage(request.toProto()).toManual()

    override suspend fun sendReaction(request: Reaction): Reaction = stub.sendReaction(request.toProto()).toManual()

    override suspend fun deleteConversation(request: DeleteConversationRequest): DeleteConversationResponse {
        val response =
            stub.deleteConversation(
                Messaging.DeleteConversationRequest
                    .newBuilder()
                    .setConversationId(request.conversationId)
                    .build(),
            )
        return DeleteConversationResponse(success = response.success)
    }
}

internal fun Reaction.toProto(): Messaging.Reaction =
    Messaging.Reaction
        .newBuilder()
        .setMessageId(messageId)
        .setConversationId(conversationId)
        .setUserId(userId)
        .setEmoji(emoji)
        .setRemoved(removed)
        .setTimestamp(timestamp)
        .build()

internal fun Messaging.Reaction.toManual(): Reaction =
    Reaction(
        messageId = messageId,
        conversationId = conversationId,
        userId = userId,
        emoji = emoji,
        removed = removed,
        timestamp = timestamp,
    )

private fun StartDirectConversationRequest.toProto(): Messaging.StartDirectConversationRequest =
    Messaging.StartDirectConversationRequest
        .newBuilder()
        .setRecipientId(recipientId)
        .build()

private fun DeviceMessage.toProto(): Messaging.DeviceMessage =
    Messaging.DeviceMessage
        .newBuilder()
        .setRecipientId(recipientId)
        .setDeviceId(deviceId)
        .setCiphertext(ByteString.copyFrom(cipherText))
        .build()

private fun SendMessageRequest.toProto(): Messaging.SendMessageRequest =
    Messaging.SendMessageRequest
        .newBuilder()
        .setConversationId(conversationId)
        .addAllDeviceMessages(deviceMessages.map(DeviceMessage::toProto))
        .setContentType(contentType)
        .setExpiresAfterSecs(expiresAfterSecs)
        .build()

private fun ClientEvent.toProto(): Messaging.ClientEvent =
    when (this) {
        is ClientEvent.Typing -> {
            val typing =
                Messaging.TypingIndicator
                    .newBuilder()
                    .setConversationId(conversationId)
                    .setUserId(userId)
                    .setIsTyping(isTyping)
                    .build()
            Messaging.ClientEvent
                .newBuilder()
                .setTyping(typing)
                .build()
        }
    }

private fun SyncRequest.toProto(): Messaging.SyncRequest =
    Messaging.SyncRequest
        .newBuilder()
        .setSinceTimestamp(sinceTimestamp)
        .build()

private fun AckedMessageRef.toProto(): Messaging.AckedMessageRef =
    Messaging.AckedMessageRef
        .newBuilder()
        .setConversationId(conversationId)
        .setMessageId(messageId)
        .build()

private fun AckMessagesRequest.toProto(): Messaging.AckMessagesRequest =
    Messaging.AckMessagesRequest
        .newBuilder()
        .addAllMessages(messages.map(AckedMessageRef::toProto))
        .build()

private fun DeleteMessageRequest.toProto(): Messaging.DeleteMessageRequest =
    Messaging.DeleteMessageRequest
        .newBuilder()
        .setConversationId(conversationId)
        .setMessageId(messageId)
        .build()

private fun ReceiptRequest.toProto(): Messaging.ReceiptRequest =
    Messaging.ReceiptRequest
        .newBuilder()
        .setConversationId(conversationId)
        .setMessageId(messageId)
        .setStatus(status)
        .build()

private fun Messaging.SendMessageResponse.toManual(): SendMessageResponse =
    SendMessageResponse(
        messageId = messageId,
        serverTimestamp = serverTimestamp,
    )

private fun Messaging.Participant.toManual(): Participant =
    Participant(
        userId = userId,
        displayName = displayName,
        avatarUrl = avatarUrl,
    )

private fun Messaging.Conversation.toManual(): Conversation =
    Conversation(
        id = id,
        type = type,
        participantIds = participantIdsList,
        unreadCount = unreadCount,
        participants = participantsList.map(Messaging.Participant::toManual),
        title = participantsList.firstOrNull { it.displayName.isNotBlank() }?.displayName.orEmpty(),
        avatarUrl = participantsList.firstOrNull { it.avatarUrl.isNotBlank() }?.avatarUrl.orEmpty(),
    )

private fun Messaging.GetConversationsResponse.toManual(): GetConversationsResponse =
    GetConversationsResponse(
        conversations = conversationsList.map(Messaging.Conversation::toManual),
    )

private fun Messaging.EncryptedEnvelope.toManual(): EncryptedEnvelope =
    EncryptedEnvelope(
        conversationId = conversationId,
        messageId = messageId,
        senderId = senderId,
        senderDevice = senderDevice,
        cipherText = ciphertext.toByteArray(),
        contentType = contentType,
        serverTimestamp = serverTimestamp,
    )

private fun Messaging.TypingIndicator.toManual(): TypingIndicator =
    TypingIndicator(
        conversationId = conversationId,
        userId = userId,
        isTyping = isTyping,
    )

private fun Messaging.ReceiptUpdate.toManual(): ReceiptUpdate =
    ReceiptUpdate(
        conversationId = conversationId,
        messageId = messageId,
        recipientId = recipientId,
        status = status,
        timestamp = timestamp,
    )

private fun Messaging.PreKeyCountLow.toManual(): PreKeyCountLow =
    PreKeyCountLow(
        deviceId = deviceId,
        remainingCount = remainingCount,
    )

private fun Messaging.CallOfferEvent.toManual(): CallOfferEvent =
    CallOfferEvent(
        callId = callId,
        callerId = callerId,
        callType = callType,
        sdpOffer = sdpOffer.toByteArray(),
        srtpKeyParams = srtpKeyParams.toByteArray(),
        encryptedSdpPayload = encryptedSdpPayload.toByteArray(),
        callerDevice = callerDevice,
    )

private fun Messaging.CallLifecycleEvent.toManual(): CallLifecycleEvent =
    CallLifecycleEvent(
        callId = callId,
        peerId = peerId,
        eventType = eventType,
        actorId = actorId,
    )

private fun Messaging.ServerEvent.toManual(): ServerEvent? =
    when (eventCase) {
        Messaging.ServerEvent.EventCase.MESSAGE -> ServerEvent.Message(message.toManual())
        Messaging.ServerEvent.EventCase.TYPING -> ServerEvent.Typing(typing.toManual())
        Messaging.ServerEvent.EventCase.RECEIPT -> ServerEvent.Receipt(receipt.toManual())
        Messaging.ServerEvent.EventCase.PRE_KEY_COUNT_LOW ->
            ServerEvent.PreKeyCountLow(preKeyCountLow.toManual())
        Messaging.ServerEvent.EventCase.CALL_OFFER -> ServerEvent.CallOffer(callOffer.toManual())
        Messaging.ServerEvent.EventCase.CALL_LIFECYCLE ->
            ServerEvent.CallLifecycle(callLifecycle.toManual())
        Messaging.ServerEvent.EventCase.REACTION -> ServerEvent.Reaction(reaction.toManual())
        Messaging.ServerEvent.EventCase.SEALED_MESSAGE,
        Messaging.ServerEvent.EventCase.MESSAGE_EDITED,
        Messaging.ServerEvent.EventCase.EVENT_NOT_SET,
        -> {
            Log.d(TAG, "Unhandled ServerEvent case: $eventCase (deferred to M3)")
            null
        }
    }

private fun DeliveryTokenRequest.toProto(): Messaging.DeliveryTokenRequest =
    Messaging.DeliveryTokenRequest
        .newBuilder()
        .setCount(count)
        .build()

private fun Messaging.DeliveryTokenResponse.toManual(): DeliveryTokenResponse =
    DeliveryTokenResponse(
        tokens = tokensList.map { it.toByteArray() },
    )

private fun SealedDeviceMessage.toProto(): Messaging.SealedDeviceMessage =
    Messaging.SealedDeviceMessage
        .newBuilder()
        .setRecipientId(recipientId)
        .setDeviceId(deviceId)
        .setSealedEnvelope(ByteString.copyFrom(sealedEnvelope))
        .setConversationId(conversationId)
        .setSilent(silent)
        .build()

private fun SendSealedMessageRequest.toProto(): Messaging.SendSealedMessageRequest =
    Messaging.SendSealedMessageRequest
        .newBuilder()
        .setDeliveryToken(ByteString.copyFrom(deliveryToken))
        .addAllDeviceMessages(deviceMessages.map(SealedDeviceMessage::toProto))
        .build()

private fun Messaging.SendSealedMessageResponse.toManual(): SendSealedMessageResponse =
    SendSealedMessageResponse(
        serverTimestamp = serverTimestamp,
    )

internal fun SealedDeviceMessage.toProtoForTest(): Messaging.SealedDeviceMessage = toProto()

internal fun Messaging.DeliveryTokenResponse.toManualForTest(): DeliveryTokenResponse = toManual()
