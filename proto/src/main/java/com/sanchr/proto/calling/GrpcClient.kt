package com.sanchr.proto.calling

import com.google.protobuf.ByteString
import io.grpc.CallOptions
import io.grpc.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import sanchr.calling.CallSignalingServiceGrpcKt
import sanchr.calling.Calling

/**
 * gRPC client interface for the CallSignalingService.
 * Adapter over the generated `sanchr.calling.CallSignalingServiceGrpcKt.CallSignalingServiceCoroutineStub`.
 */
interface CallSignalingServiceClient {
    suspend fun initiateCall(request: CallOffer): CallResponse

    /**
     * Bidirectional signaling. The first frame the caller emits must be a
     * [CallSignalPayload.Join]; the server replays any signals the peer sent
     * before this side joined, then relays live ones. Frames with no
     * recognised payload are dropped.
     */
    fun callStream(requests: Flow<CallSignal>): Flow<CallSignal>

    suspend fun endCall(request: EndCallRequest): EndCallResponse

    suspend fun getCallHistory(request: GetCallHistoryRequest): GetCallHistoryResponse

    suspend fun getTurnCredentials(request: GetTurnCredentialsRequest): TurnCredentials
}

class CallSignalingServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : CallSignalingServiceClient {
    private val stub by lazy { CallSignalingServiceGrpcKt.CallSignalingServiceCoroutineStub(channel, callOptions) }

    override suspend fun initiateCall(request: CallOffer): CallResponse = stub.initiateCall(request.toProto()).toModel()

    override fun callStream(requests: Flow<CallSignal>): Flow<CallSignal> =
        stub.callStream(requests.map { it.toProto() }).mapNotNull { it.toModel() }

    override suspend fun endCall(request: EndCallRequest): EndCallResponse {
        stub.endCall(
            Calling.EndCallRequest
                .newBuilder()
                .setCallId(request.callId)
                .setReason(request.reason)
                .build(),
        )
        return EndCallResponse
    }

    override suspend fun getCallHistory(request: GetCallHistoryRequest): GetCallHistoryResponse =
        stub
            .getCallHistory(
                Calling.GetCallHistoryRequest
                    .newBuilder()
                    .setLimit(request.limit)
                    .build(),
            ).toModel()

    override suspend fun getTurnCredentials(request: GetTurnCredentialsRequest): TurnCredentials =
        stub.getTurnCredentials(Calling.GetTurnCredentialsRequest.getDefaultInstance()).toModel()
}

internal fun CallOffer.toProto(): Calling.CallOffer =
    Calling.CallOffer
        .newBuilder()
        .setRecipientId(recipientId)
        .setCallType(callType)
        .addAllDeviceOffers(
            deviceOffers.map {
                Calling.DeviceCallOffer
                    .newBuilder()
                    .setDeviceId(it.deviceId)
                    .setEncryptedSdpPayload(ByteString.copyFrom(it.encryptedSdpPayload))
                    .build()
            },
        ).setEncryptedSdpPayload(ByteString.copyFrom(legacyEncryptedSdpPayload))
        .build()

internal fun Calling.CallResponse.toModel(): CallResponse = CallResponse(callId = callId, status = status)

internal fun CallSignal.toProto(): Calling.CallSignal {
    val builder =
        Calling.CallSignal
            .newBuilder()
            .setCallId(callId)
            .setPeerDevice(peerDevice)
    when (val p = payload) {
        is CallSignalPayload.IceCandidate -> builder.setIceCandidate(ByteString.copyFrom(p.json))
        is CallSignalPayload.Control -> builder.setControl(Calling.CallControl.newBuilder().setAction(p.action))
        is CallSignalPayload.EncryptedSdpAnswer -> builder.setEncryptedSdpAnswer(ByteString.copyFrom(p.ciphertext))
        is CallSignalPayload.Join ->
            builder.setJoin(
                Calling.CallJoin
                    .newBuilder()
                    .setRole(p.role)
                    .setAnswererDevice(p.answererDevice),
            )
    }
    return builder.build()
}

/** Null for a frame carrying no recognised payload (a future oneof case, or none). */
internal fun Calling.CallSignal.toModel(): CallSignal? {
    val payload =
        when (signalCase) {
            Calling.CallSignal.SignalCase.ICE_CANDIDATE -> CallSignalPayload.IceCandidate(iceCandidate.toByteArray())
            Calling.CallSignal.SignalCase.CONTROL -> CallSignalPayload.Control(control.action)
            Calling.CallSignal.SignalCase.ENCRYPTED_SDP_ANSWER -> CallSignalPayload.EncryptedSdpAnswer(encryptedSdpAnswer.toByteArray())
            Calling.CallSignal.SignalCase.JOIN -> CallSignalPayload.Join(join.role, join.answererDevice)
            Calling.CallSignal.SignalCase.SIGNAL_NOT_SET, null -> return null
        }
    return CallSignal(callId = callId, peerDevice = peerDevice, payload = payload)
}

internal fun Calling.GetCallHistoryResponse.toModel(): GetCallHistoryResponse =
    GetCallHistoryResponse(
        entries =
            entriesList.map {
                CallLogEntry(
                    callId = it.callId,
                    peerId = it.peerId,
                    peerName = it.peerName,
                    callType = it.callType,
                    direction = it.direction,
                    status = it.status,
                    startedAt = it.startedAt,
                    endedAt = it.endedAt,
                    durationSecs = it.durationSecs,
                )
            },
    )

internal fun Calling.TurnCredentials.toModel(): TurnCredentials =
    TurnCredentials(urls = urlsList, username = username, credential = credential, ttlSecs = ttl)
