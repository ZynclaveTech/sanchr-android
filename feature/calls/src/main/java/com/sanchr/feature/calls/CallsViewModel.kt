package com.sanchr.feature.calls

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.callengine.CallManager
import com.sanchr.core.callengine.CallState
import com.sanchr.core.callengine.WebRTCClient
import com.sanchr.proto.calling.CallLogEntry
import com.sanchr.proto.calling.CallSignalingServiceClient
import com.sanchr.proto.calling.GetCallHistoryRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CallFilter(
    val label: String,
) {
    ALL("All"),
    MISSED("Missed"),
    INCOMING("Incoming"),
    OUTGOING("Outgoing"),
}

data class CallsListUiState(
    val entries: List<CallLogEntry> = emptyList(),
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
        val webRTCClient: WebRTCClient,
    ) : ViewModel() {
        companion object {
            private const val TAG = "CallsViewModel"
            private const val PAGE_SIZE = 50
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

        // -----------------------------------------------------------------------
        // Call history list state
        // -----------------------------------------------------------------------

        private val _allEntries = MutableStateFlow<List<CallLogEntry>>(emptyList())
        private val _filter = MutableStateFlow(CallFilter.ALL)
        private val _isLoading = MutableStateFlow(true)
        private val _isRefreshing = MutableStateFlow(false)
        private val _nextPageToken = MutableStateFlow("")

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
                        CallFilter.MISSED -> entries.filter { it.status == "missed" }
                        CallFilter.INCOMING ->
                            entries.filter {
                                it.status != "missed" && it.callerId != ""
                            }
                        CallFilter.OUTGOING ->
                            entries.filter {
                                it.status != "missed" && it.calleeId != ""
                            }
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
            _nextPageToken.value = ""
            loadCallHistory()
        }

        private fun loadCallHistory(pageToken: String = "") {
            viewModelScope.launch {
                try {
                    if (pageToken.isEmpty()) {
                        _isLoading.value = _allEntries.value.isEmpty()
                    }

                    val response =
                        callSignalingServiceClient.getCallHistory(
                            GetCallHistoryRequest(
                                pageToken = pageToken,
                                pageSize = PAGE_SIZE,
                            ),
                        )

                    if (pageToken.isEmpty()) {
                        _allEntries.value = response.entries
                    } else {
                        _allEntries.update { current -> current + response.entries }
                    }

                    _nextPageToken.value = response.nextPageToken
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load call history", e)
                } finally {
                    _isLoading.value = false
                    _isRefreshing.value = false
                }
            }
        }

        fun deleteCallLogEntry(callId: String) {
            _allEntries.update { entries ->
                entries.filter { it.callId != callId }
            }
        }

        // -----------------------------------------------------------------------
        // Call actions
        // -----------------------------------------------------------------------

        fun startVoiceCall(
            userId: String,
            userName: String = userId,
        ) {
            viewModelScope.launch {
                callManager.startCall(
                    recipientId = userId,
                    recipientName = userName,
                    isVideo = false,
                )
            }
        }

        fun startVideoCall(
            userId: String,
            userName: String = userId,
        ) {
            viewModelScope.launch {
                callManager.startCall(
                    recipientId = userId,
                    recipientName = userName,
                    isVideo = true,
                )
            }
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
    }

sealed interface CallsEvent {
    data class Error(
        val message: String,
    ) : CallsEvent
}
