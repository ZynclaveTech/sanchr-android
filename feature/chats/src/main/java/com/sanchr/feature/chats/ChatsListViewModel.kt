package com.sanchr.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.common.Result
import com.sanchr.core.model.Conversation
import com.sanchr.domain.messaging.ObserveConversationsUseCase
import com.sanchr.proto.messaging.GetConversationsRequest
import com.sanchr.proto.messaging.MessagingServiceClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChatsListUiState {
    data object Loading : ChatsListUiState
    data class Success(
        val conversations: List<Conversation>,
        val searchQuery: String = "",
        val isRefreshing: Boolean = false,
    ) : ChatsListUiState

    data object Empty : ChatsListUiState
    data class Error(val message: String) : ChatsListUiState
}

@HiltViewModel
class ChatsListViewModel @Inject constructor(
    observeConversationsUseCase: ObserveConversationsUseCase,
    private val messagingServiceClient: MessagingServiceClient,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<ChatsListUiState> = combine(
        observeConversationsUseCase(),
        _searchQuery,
        _isRefreshing,
    ) { result, query, refreshing ->
        when (result) {
            is Result.Loading -> ChatsListUiState.Loading

            is Result.Success -> {
                val filtered = if (query.isBlank()) {
                    result.data
                } else {
                    result.data.filter { conversation ->
                        conversation.title?.contains(query, ignoreCase = true) == true
                    }
                }
                if (filtered.isEmpty() && query.isBlank()) {
                    ChatsListUiState.Empty
                } else {
                    ChatsListUiState.Success(
                        conversations = filtered,
                        searchQuery = query,
                        isRefreshing = refreshing,
                    )
                }
            }

            is Result.Error -> ChatsListUiState.Error(
                message = result.exception.message ?: "Failed to load conversations",
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatsListUiState.Loading,
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Trigger a server sync to refresh conversation data
                messagingServiceClient.getConversations(
                    GetConversationsRequest(pageSize = 50),
                )
            } catch (_: Exception) {
                // Silently fail refresh; local data is still available
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
