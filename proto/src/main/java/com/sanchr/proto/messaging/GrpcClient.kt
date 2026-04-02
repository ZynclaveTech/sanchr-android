package com.sanchr.proto.messaging

import io.grpc.CallOptions
import io.grpc.Channel
import kotlinx.coroutines.flow.Flow

/**
 * gRPC client interface for the MessagingService.
 * Generated stub equivalent for vync.messaging.MessagingService.
 */
interface MessagingServiceClient {

    suspend fun sendMessage(request: SendMessageRequest): SendMessageResponse

    suspend fun startDirectConversation(request: StartDirectConversationRequest): Conversation

    /**
     * Bidirectional streaming RPC for real-time messaging.
     * Client sends [ClientEvent]s, server responds with [ServerEvent]s.
     */
    fun messageStream(requests: Flow<ClientEvent>): Flow<ServerEvent>

    /**
     * Server-streaming RPC for syncing missed messages.
     */
    fun syncMessages(request: SyncRequest): Flow<EncryptedEnvelope>

    suspend fun deleteMessage(request: DeleteMessageRequest): DeleteMessageResponse

    suspend fun sendReceipt(request: ReceiptRequest): ReceiptResponse

    suspend fun getConversations(request: GetConversationsRequest): GetConversationsResponse
}

/**
 * Implementation shell that will delegate to the actual gRPC-generated stubs
 * once protobuf-gradle-plugin codegen runs.
 */
class MessagingServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : MessagingServiceClient {

    override suspend fun sendMessage(request: SendMessageRequest): SendMessageResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun startDirectConversation(
        request: StartDirectConversationRequest,
    ): Conversation {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override fun messageStream(requests: Flow<ClientEvent>): Flow<ServerEvent> {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override fun syncMessages(request: SyncRequest): Flow<EncryptedEnvelope> {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun deleteMessage(request: DeleteMessageRequest): DeleteMessageResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun sendReceipt(request: ReceiptRequest): ReceiptResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun getConversations(
        request: GetConversationsRequest,
    ): GetConversationsResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }
}
