package com.sanchr.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.sanchr.core.common.Result
import com.sanchr.core.model.Conversation
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.ObserveConversationsUseCase
import com.sanchr.sync.SyncState
import com.sanchr.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

/** UI state for the "new conversation by phone" bottom sheet. */
data class NewChatState(
    val isOpen: Boolean = false,
    val phone: String = "",
    val isSubmitting: Boolean = false,
    val error: NewChatError? = null,
)

enum class NewChatError { INVALID_PHONE, NOT_FOUND, SERVER_ERROR }

/** One-shot navigation events emitted by the chats-list VM. */
sealed interface NewChatEvent {
    data class OpenConversation(
        val conversationId: String,
    ) : NewChatEvent
}

@HiltViewModel
class ChatsListViewModel
    @Inject
    constructor(
        observeConversationsUseCase: ObserveConversationsUseCase,
        private val workManager: WorkManager,
        private val contactRepository: ContactRepository,
        private val messageRepository: MessageRepository,
        val syncState: SyncState,
    ) : ViewModel() {
        private val _searchQuery = MutableStateFlow("")
        private val _isRefreshing = MutableStateFlow(false)

        private val _newChat = MutableStateFlow(NewChatState())
        val newChat: StateFlow<NewChatState> = _newChat.asStateFlow()

        private val _events =
            MutableSharedFlow<NewChatEvent>(
                replay = 0,
                extraBufferCapacity = 1,
            )
        val events: SharedFlow<NewChatEvent> = _events.asSharedFlow()

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

        // ── New-chat bottom sheet ──────────────────────────────────────────

        fun openNewChat() {
            _newChat.value = NewChatState(isOpen = true)
        }

        fun closeNewChat() {
            _newChat.value = NewChatState()
        }

        fun onNewChatPhoneChanged(phone: String) {
            _newChat.value = _newChat.value.copy(phone = phone, error = null)
        }

        fun submitNewChat() {
            val current = _newChat.value
            if (current.isSubmitting) return
            val phone = current.phone.trim()
            if (!E164_PATTERN.matches(phone)) {
                _newChat.value = current.copy(error = NewChatError.INVALID_PHONE)
                return
            }
            _newChat.value = current.copy(isSubmitting = true, error = null)
            viewModelScope.launch {
                val user =
                    try {
                        contactRepository.lookupByPhone(phone)
                    } catch (cancellation: kotlinx.coroutines.CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        _newChat.value =
                            _newChat.value.copy(
                                isSubmitting = false,
                                error = NewChatError.SERVER_ERROR,
                            )
                        return@launch
                    }
                if (user == null) {
                    _newChat.value =
                        _newChat.value.copy(
                            isSubmitting = false,
                            error = NewChatError.NOT_FOUND,
                        )
                    return@launch
                }
                val conversationId =
                    try {
                        messageRepository.ensureConversation(user.id)
                    } catch (cancellation: kotlinx.coroutines.CancellationException) {
                        throw cancellation
                    } catch (_: Throwable) {
                        _newChat.value =
                            _newChat.value.copy(
                                isSubmitting = false,
                                error = NewChatError.SERVER_ERROR,
                            )
                        return@launch
                    }
                _events.tryEmit(NewChatEvent.OpenConversation(conversationId))
                _newChat.value = NewChatState()
            }
        }

        private companion object {
            /** Minimal E.164 check: leading + followed by 7–15 digits. */
            private val E164_PATTERN = Regex("^\\+\\d{7,15}$")
        }
    }
