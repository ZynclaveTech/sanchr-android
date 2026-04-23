package com.sanchr.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.sanchr.core.common.Result
import com.sanchr.core.model.Conversation
import com.sanchr.domain.messaging.ObserveConversationsUseCase
import com.sanchr.sync.SyncState
import com.sanchr.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

sealed interface ChatsListUiState {
    data object Loading : ChatsListUiState

    data class Success(
        val conversations: List<Conversation>,
        val searchQuery: String = "",
        val isRefreshing: Boolean = false,
        val isSyncing: Boolean = false,
    ) : ChatsListUiState

    data object Empty : ChatsListUiState

    data class Error(
        val message: String,
    ) : ChatsListUiState
}

@HiltViewModel
class ChatsListViewModel
    @Inject
    constructor(
        observeConversationsUseCase: ObserveConversationsUseCase,
        private val workManager: WorkManager,
        val syncState: SyncState,
    ) : ViewModel() {
        private val _searchQuery = MutableStateFlow("")
        private val _isRefreshing = MutableStateFlow(false)

        val uiState: StateFlow<ChatsListUiState> =
            combine(
                observeConversationsUseCase(),
                _searchQuery,
                _isRefreshing,
                syncState.isSyncing,
            ) { result, query, refreshing, syncing ->
                when (result) {
                    is Result.Loading -> ChatsListUiState.Loading

                    is Result.Success -> {
                        val filtered =
                            if (query.isBlank()) {
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
                                isSyncing = syncing,
                            )
                        }
                    }

                    is Result.Error ->
                        ChatsListUiState.Error(
                            message = result.exception.message ?: "Failed to load conversations",
                        )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ChatsListUiState.Loading,
            )

        init {
            // When a background sync completes, clear the manual refresh indicator.
            // This ensures the pull-to-refresh spinner dismisses once data arrives.
            syncState.isSyncing
                .onEach { syncing ->
                    if (!syncing && _isRefreshing.value) {
                        _isRefreshing.value = false
                    }
                }.launchIn(viewModelScope)
        }

        fun onSearchQueryChanged(query: String) {
            _searchQuery.value = query
        }

        /**
         * Pull-to-refresh handler. Triggers a one-time background sync via
         * WorkManager, which will fetch new messages and conversations from the
         * server.
         */
        fun refresh() {
            _isRefreshing.value = true
            SyncWorker.syncNow(workManager)
        }
    }
