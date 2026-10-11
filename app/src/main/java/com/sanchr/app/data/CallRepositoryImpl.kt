package com.sanchr.app.data

import com.sanchr.domain.calls.CallRecord
import com.sanchr.domain.calls.CallRepository
import com.sanchr.proto.calling.CallLogEntry
import com.sanchr.proto.calling.CallSignalingServiceClient
import com.sanchr.proto.calling.GetCallHistoryRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Singleton
class CallRepositoryImpl
    @Inject
    constructor(
        private val callClient: CallSignalingServiceClient,
    ) : CallRepository {
        override fun observeCallHistory(limit: Int): Flow<List<CallRecord>> =
            flow {
                emit(callClient.getCallHistory(GetCallHistoryRequest(limit = limit)).entries.map { it.toRecord() })
            }

        private fun CallLogEntry.toRecord(): CallRecord =
            CallRecord(
                id = callId,
                remoteUserId = peerId,
                // Never the server's plaintext peer_name; resolved through the contact layer.
                remoteUserName = "",
                remoteUserAvatarUrl = null,
                isOutgoing = direction == "outgoing",
                isVideo = callType == "video",
                durationSeconds = durationSecs.toLong(),
                // The server speaks seconds; everything on this side keeps time in milliseconds.
                timestamp = startedAt * MILLIS_PER_SECOND,
                isMissed = status == "missed",
            )

        private companion object {
            const val MILLIS_PER_SECOND = 1_000L
        }
    }
