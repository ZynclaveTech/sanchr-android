package com.sanchr.feature.calls

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.callengine.CallManager
import com.sanchr.core.callengine.CallPlatform
import com.sanchr.core.callengine.CallState
import com.sanchr.core.callengine.WebRTCClient
import com.sanchr.core.common.calls.CallPeerNames
import com.sanchr.proto.calling.CallLogEntry
import com.sanchr.proto.calling.CallSignalingServiceClient
import com.sanchr.proto.calling.GetCallHistoryRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CallFilter(
    val label: String,
) {
    ALL("All"),
    MISSED("Missed"),
    INCOMING("Incoming"),
    OUTGOING("Outgoing"),
}

/** A call-log row with the peer's name resolved through the contact layer (never the server's plaintext). */
data class CallLogItem(
    val entry: CallLogEntry,
    val displayName: String,
) {
    val isOutgoing: Boolean get() = entry.direction == "outgoing"
    val isMissed: Boolean get() = entry.status == "missed"
    val isVideo: Boolean get() = entry.callType == "video"
}

data class CallsListUiState(
    val entries: List<CallLogItem> = emptyList(),
    val filter: CallFilter = CallFilter.ALL,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CallsViewModel
    @Inject
    constructor(
        private val callSignalingServiceClient: CallSignalingServiceClient,
        private val callManager: CallManager,
        private val peerNames: CallPeerNames,
        val webRTCClient: WebRTCClient,
        private val callPlatform: CallPlatform,
    ) : ViewModel() {
        companion object {
            private const val TAG = "CallsViewModel"
            private const val HISTORY_LIMIT = 50
        }

        // -----------------------------------------------------------------------
        // Call state (from CallManager)
        // -----------------------------------------------------------------------

        val callState: StateFlow<CallState> =
            callManager.callState
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = CallState.Idle,
                )

        val isMuted: StateFlow<Boolean> =
            callManager.isMuted
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = false,
                )

        val isSpeakerOn: StateFlow<Boolean> =
            callManager.isSpeakerOn
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = false,
                )

        val isVideoEnabled: StateFlow<Boolean> =
            callManager.isVideoEnabled
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = true,
                )

        val callDuration: StateFlow<Long> =
            callManager.callDuration
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = 0L,
                )

        val callType: StateFlow<String> =
            callManager.callType
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = "voice",
                )

        val peerVideoEnabled: StateFlow<Boolean> =
            callManager.peerVideoEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val hasRemoteVideoTrack: StateFlow<Boolean> =
            callManager.hasRemoteVideoTrack.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val incomingVideoUpgradeRequest: StateFlow<Boolean> =
            callManager.incomingVideoUpgradeRequest.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val outgoingVideoUpgradePending: StateFlow<Boolean> =
            callManager.outgoingVideoUpgradePending.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        // -----------------------------------------------------------------------
        // Call history list state
        // -----------------------------------------------------------------------

        private val _allEntries = MutableStateFlow<List<CallLogItem>>(emptyList())
        private val _filter = MutableStateFlow(CallFilter.ALL)
        private val _isLoading = MutableStateFlow(true)
        private val _isRefreshing = MutableStateFlow(false)

        private val _events = MutableSharedFlow<CallsEvent>()
        val events = _events.asSharedFlow()

        val uiState: StateFlow<CallsListUiState> =
            combine(
                _allEntries,
                _filter,
                _isLoading,
                _isRefreshing,
            ) { entries, filter, loading, refreshing ->
                val filtered =
                    when (filter) {
                        CallFilter.ALL -> entries
                        CallFilter.MISSED -> entries.filter { it.isMissed }
                        CallFilter.INCOMING -> entries.filter { !it.isMissed && !it.isOutgoing }
                        CallFilter.OUTGOING -> entries.filter { !it.isMissed && it.isOutgoing }
                    }

                CallsListUiState(
                    entries = filtered,
                    filter = filter,
                    isLoading = loading,
                    isRefreshing = refreshing,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = CallsListUiState(),
            )

        init {
            loadCallHistory()
        }

        // -----------------------------------------------------------------------
        // Call history actions
        // -----------------------------------------------------------------------

        fun setFilter(filter: CallFilter) {
            _filter.value = filter
        }

        fun refresh() {
            _isRefreshing.value = true
            loadCallHistory()
        }

        private fun loadCallHistory() {
            viewModelScope.launch {
                try {
                    _isLoading.value = _allEntries.value.isEmpty()
                    val response = callSignalingServiceClient.getCallHistory(GetCallHistoryRequest(limit = HISTORY_LIMIT))
                    val names =
                        response.entries
                            .map { it.peerId }
                            .distinct()
                            .associateWith { peerNames.displayNameFor(it) }
                    _allEntries.value = response.entries.map { CallLogItem(it, names.getValue(it.peerId)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load call history", e)
                } finally {
                    _isLoading.value = false
                    _isRefreshing.value = false
                }
            }
        }

        // -----------------------------------------------------------------------
        // Call actions
        // -----------------------------------------------------------------------

        /** Places a call through the foreground call service; the caller must already hold the runtime permissions. */
        fun startCall(
            userId: String,
            userName: String,
            isVideo: Boolean,
        ) {
            if (callManager.callState.value !is CallState.Idle) return
            callPlatform.startOutgoingCallService(recipientId = userId, recipientName = userName.ifBlank { userId }, isVideo = isVideo)
        }

        fun answerCall() {
            viewModelScope.launch {
                callManager.answerCall()
            }
        }

        fun declineCall() {
            callManager.declineCall()
        }

        fun endCall() {
            viewModelScope.launch {
                callManager.endCall()
            }
        }

        fun toggleMute() = callManager.toggleMute()

        fun toggleSpeaker() = callManager.toggleSpeaker()

        fun toggleVideo() = callManager.toggleVideo()

        fun switchCamera() = callManager.switchCamera()

        fun acceptVideoUpgrade() = callManager.acceptVideoUpgrade()

        fun declineVideoUpgrade() = callManager.declineVideoUpgrade()
    }

sealed interface CallsEvent {
    data class Error(
        val message: String,
    ) : CallsEvent
}
