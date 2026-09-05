package com.sanchr.domain.calls

import kotlinx.coroutines.flow.Flow

/**
 * Call history. Live signaling is the call engine's (`CallManager`), which
 * owns the WebRTC session the SDP must come from; there is no way to
 * "initiate a call" from a repository without one.
 */
interface CallRepository {
    /** The server's call log, newest first. */
    fun observeCallHistory(limit: Int = DEFAULT_HISTORY_LIMIT): Flow<List<CallRecord>>

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 50
    }
}

data class CallRecord(
    val id: String,
    val remoteUserId: String,
    /** Resolved by the caller through the contact layer; empty here. */
    val remoteUserName: String,
    val remoteUserAvatarUrl: String?,
    val isOutgoing: Boolean,
    val isVideo: Boolean,
    val durationSeconds: Long,
    val timestamp: Long,
    val isMissed: Boolean,
)
