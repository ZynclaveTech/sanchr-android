package com.sanchr.domain.calls

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for call history and signaling operations.
 */
interface CallRepository {
    /** Observes the call history log. */
    fun observeCallHistory(): Flow<List<CallRecord>>

    /** Initiates a call via the signaling server. */
    suspend fun initiateCall(userId: String, isVideo: Boolean): String // Returns call ID

    /** Sends a hangup signal for an active call. */
    suspend fun endCall(callId: String)

    /** Sends an SDP offer/answer via signaling. */
    suspend fun sendSignal(
        callId: String,
        type: String,
        payload: String,
    )

    /** Observes incoming signals for a call. */
    fun observeSignals(callId: String): Flow<CallSignal>
}

data class CallRecord(
    val id: String,
    val remoteUserId: String,
    val remoteUserName: String,
    val remoteUserAvatarUrl: String?,
    val isOutgoing: Boolean,
    val isVideo: Boolean,
    val durationSeconds: Long,
    val timestamp: Long,
    val isMissed: Boolean,
)

data class CallSignal(
    val type: String, // "offer", "answer", "ice-candidate", "hangup"
    val payload: String,
)
