package com.sanchr.feature.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.callengine.CallManager
import com.sanchr.core.callengine.CallState
import com.sanchr.domain.calls.CallRecord
import com.sanchr.domain.calls.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CallsListUiState(
    val callHistory: List<CallRecord> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class CallsViewModel @Inject constructor(
    private val callRepository: CallRepository,
    private val callManager: CallManager,
) : ViewModel() {

    val callState: StateFlow<CallState> = callManager.callState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CallState.Idle,
        )

    private val _uiState = MutableStateFlow(CallsListUiState())
    val uiState: StateFlow<CallsListUiState> = _uiState.asStateFlow()

    init {
        observeCallHistory()
    }

    private fun observeCallHistory() {
        viewModelScope.launch {
            callRepository.observeCallHistory().collect { history ->
                _uiState.value = CallsListUiState(
                    callHistory = history,
                    isLoading = false,
                )
            }
        }
    }

    fun startVoiceCall(userId: String) {
        viewModelScope.launch {
            callManager.startCall(userId, isVideo = false)
        }
    }

    fun startVideoCall(userId: String) {
        viewModelScope.launch {
            callManager.startCall(userId, isVideo = true)
        }
    }

    fun endCall() {
        viewModelScope.launch {
            callManager.endCall()
        }
    }

    fun toggleMute() = callManager.toggleMute()

    fun toggleSpeaker() = callManager.toggleSpeaker()
}
