package com.sanchr.proto.calling

import io.grpc.CallOptions
import io.grpc.Channel
import kotlinx.coroutines.flow.Flow

/**
 * gRPC client interface for the CallSignalingService.
 * Generated stub equivalent for sanchr.calling.CallSignalingService.
 */
interface CallSignalingServiceClient {
    suspend fun initiateCall(request: CallOffer): CallResponse

    /**
     * Bidirectional streaming RPC for real-time call signaling.
     */
    fun callStream(requests: Flow<CallSignal>): Flow<CallSignal>

    suspend fun endCall(request: EndCallRequest): EndCallResponse

    suspend fun getCallHistory(request: GetCallHistoryRequest): GetCallHistoryResponse

    suspend fun getTurnCredentials(request: GetTurnCredentialsRequest): TurnCredentials
}

/**
 * Implementation shell that will delegate to the actual gRPC-generated stubs
 * once the calling feature lands.
 */
class CallSignalingServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : CallSignalingServiceClient {
    // TODO(M6+): wire to CallSignalingServiceGrpcKt.CallSignalingServiceCoroutineStub when calling lands
    override suspend fun initiateCall(request: CallOffer): CallResponse = throw NotImplementedError("Awaiting calling feature in M6+")

    // TODO(M6+): wire to CallSignalingServiceGrpcKt.CallSignalingServiceCoroutineStub when calling lands
    override fun callStream(requests: Flow<CallSignal>): Flow<CallSignal> = throw NotImplementedError("Awaiting calling feature in M6+")

    // TODO(M6+): wire to CallSignalingServiceGrpcKt.CallSignalingServiceCoroutineStub when calling lands
    override suspend fun endCall(request: EndCallRequest): EndCallResponse = throw NotImplementedError("Awaiting calling feature in M6+")

    // TODO(M6+): wire to CallSignalingServiceGrpcKt.CallSignalingServiceCoroutineStub when calling lands
    override suspend fun getCallHistory(request: GetCallHistoryRequest): GetCallHistoryResponse =
        throw NotImplementedError("Awaiting calling feature in M6+")

    // TODO(M6+): wire to CallSignalingServiceGrpcKt.CallSignalingServiceCoroutineStub when calling lands
    override suspend fun getTurnCredentials(request: GetTurnCredentialsRequest): TurnCredentials =
        throw NotImplementedError("Awaiting calling feature in M6+")
}
