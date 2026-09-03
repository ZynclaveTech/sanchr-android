package com.sanchr.app.data

import com.sanchr.domain.calls.CallRecord
import com.sanchr.domain.calls.CallRepository
import com.sanchr.domain.calls.CallSignal
import com.sanchr.proto.calling.CallOffer
import com.sanchr.proto.calling.CallSignal as ProtoCallSignal
import com.sanchr.proto.calling.CallSignalingServiceClient
import com.sanchr.proto.calling.EndCallRequest
import com.sanchr.proto.calling.GetCallHistoryRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

@Singleton
class CallRepositoryImpl
    @Inject
    constructor(
        private val callClient: CallSignalingServiceClient,
    ) : CallRepository {
        override fun observeCallHistory(): Flow<List<CallRecord>> =
            flow {
                val response = callClient.getCallHistory(GetCallHistoryRequest())
                emit(
                    response.entries.map { entry ->
                        CallRecord(
                            id = entry.callId,
                            remoteUserId = entry.calleeId,
                            remoteUserName = "", // Not available from proto; resolved in UI layer
                            remoteUserAvatarUrl = null,
                            isOutgoing = true, // Determined by comparing with current user ID in UI
                            isVideo = entry.callType == "video",
                            durationSeconds = entry.durationSeconds.toLong(),
                            timestamp = entry.startedAt,
                            isMissed = entry.status == "missed",
                        )
                    },
                )
            }

        override suspend fun initiateCall(
            userId: String,
            isVideo: Boolean,
        ): String {
            val response =
                callClient.initiateCall(
                    CallOffer(
                        calleeId = userId,
                        callType = if (isVideo) "video" else "audio",
                        timestamp = System.currentTimeMillis(),
                    ),
                )
            return response.callId
        }

        override suspend fun endCall(callId: String) {
            callClient.endCall(EndCallRequest(callId = callId))
        }

        override suspend fun sendSignal(
            callId: String,
            type: String,
            payload: String,
        ) {
            // Send signal through the bidirectional stream by wrapping in a single-shot flow
            val signalFlow = MutableSharedFlow<ProtoCallSignal>(replay = 1)
            val protoSignal =
                when (type) {
                    "ice-candidate" -> ProtoCallSignal.IceCandidate(callId = callId, candidate = payload)
                    "answer" -> ProtoCallSignal.SdpAnswer(callId = callId, sdp = payload)
                    else -> ProtoCallSignal.Control(callId = callId, action = type)
                }
            signalFlow.emit(protoSignal)
            // Collect one response to confirm delivery
            callClient.callStream(signalFlow)
        }

        override fun observeSignals(callId: String): Flow<CallSignal> =
            callbackFlow {
                val outgoing = MutableSharedFlow<ProtoCallSignal>(replay = 1)
                // Send an initial control signal to subscribe to the call's signal stream
                outgoing.emit(ProtoCallSignal.Control(callId = callId, action = "subscribe"))

                val job =
                    launch {
                        callClient.callStream(outgoing).collect { protoSignal ->
                            val signal =
                                when (protoSignal) {
                                    is ProtoCallSignal.SdpAnswer ->
                                        CallSignal(
                                            type = "answer",
                                            payload = protoSignal.sdp,
                                        )
                                    is ProtoCallSignal.IceCandidate ->
                                        CallSignal(
                                            type = "ice-candidate",
                                            payload = protoSignal.candidate,
                                        )
                                    is ProtoCallSignal.Control ->
                                        CallSignal(
                                            type = protoSignal.action,
                                            payload = "",
                                        )
                                }
                            trySend(signal)
                        }
                    }

                awaitClose { job.cancel() }
            }
    }
