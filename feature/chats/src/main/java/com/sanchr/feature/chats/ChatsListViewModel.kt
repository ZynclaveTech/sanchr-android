package com.sanchr.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.common.Result
import com.sanchr.core.model.Conversation
import com.sanchr.domain.messaging.ObserveConversationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ChatsListUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val searchQuery: String = "",
)

@HiltViewModel
class ChatsListViewModel @Inject constructor(
    observeConversationsUseCase: ObserveConversationsUseCase,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<ChatsListUiState> = observeConversationsUseCase()
        .map { result ->
            when (result) {
                is Result.Loading -> ChatsListUiState(isLoading = true)
                is Result.Success -> ChatsListUiState(
                    conversations = result.data,
                    isLoading = false,
                )
                is Result.Error -> ChatsListUiState(
                    isLoading = false,
                    errorMessage = result.exception.message,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChatsListUiState(),
        )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        // TODO: Filter conversations by search query
    }
}
