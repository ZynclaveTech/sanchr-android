package com.sanchr.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.sanchr.core.common.Result
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.User
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.ObserveConversationsUseCase
import com.sanchr.sync.SyncState
import com.sanchr.sync.SyncWorker
import com.sanchr.sync.realtime.RealtimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
        /** Who "we" are, so a row can tell an outgoing last message from an incoming one. */
        val currentUserId: String = "",
        /** Conversations whose peer is typing right now; the row says so instead of the preview, as iOS. */
        val typingConversationIds: Set<String> = emptySet(),
    ) : ChatsListUiState

    data object Empty : ChatsListUiState

    data class Error(
        val message: String,
    ) : ChatsListUiState
}

/**
 * UI state for the new-chat contact-picker bottom sheet.
 *
 * Mirrors iOS NewChatContactPickerSheet (Features/Chats/Presentation/
 * NewChatContactPicker.swift): a list of already-synced contacts plus a
 * search filter. Phone-number lookup is deliberately NOT here — it lives on
 * the Contacts tab's "Add contact" entry, matching iOS.
 *
 * `contacts` is the unfiltered roster. `filteredContacts` is the live view
 * after applying [searchQuery] (case-insensitive substring on display name,
 * phone, and bio). The split is materialized in state rather than recomputed
 * in the composable so tests can assert filtering without mounting Compose.
 */
data class NewChatPickerState(
    val isOpen: Boolean = false,
    val contacts: List<User> = emptyList(),
    val filteredContacts: List<User> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

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
        private val sessionManager: SessionManager,
        realtimeManager: RealtimeManager,
    ) : ViewModel() {
        private val _searchQuery = MutableStateFlow("")
        private val _isRefreshing = MutableStateFlow(false)

        private val _picker = MutableStateFlow(NewChatPickerState())
        val picker: StateFlow<NewChatPickerState> = _picker.asStateFlow()

        /**
         * Tracks the in-flight contact-load job so reopening the sheet
         * cancels stale work instead of racing two concurrent loads.
         */
        private var contactLoadJob: Job? = null

        /** Tracks an in-flight ensureConversation so double-taps no-op. */
        private var startConversationJob: Job? = null

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
                realtimeManager.typingCache,
            ) { result, query, refreshing, syncing, typing ->
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
                                currentUserId = sessionManager.getUserId().orEmpty(),
                                typingConversationIds = typing.filterValues { it.isTyping }.keys,
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

        // ── New-chat contact picker ────────────────────────────────────────

        /**
         * Opens the picker sheet and starts loading the synced-contact roster.
         * Cancels any prior load to keep state monotonic.
         */
        fun openNewChatPicker() {
            contactLoadJob?.cancel()
            _picker.value =
                NewChatPickerState(
                    isOpen = true,
                    isLoading = true,
                )
            contactLoadJob = viewModelScope.launch { loadContacts() }
        }

        fun closeNewChatPicker() {
            contactLoadJob?.cancel()
            startConversationJob?.cancel()
            _picker.value = NewChatPickerState()
        }

        /**
         * Updates the search query and recomputes the filtered list. Filter is
         * a case-insensitive substring match on display name, phone, and bio
         * (matches iOS NewChatContactPickerSheet.filteredContacts).
         */
        fun onPickerSearchQueryChanged(query: String) {
            val current = _picker.value
            _picker.value =
                current.copy(
                    searchQuery = query,
                    filteredContacts = filterContacts(current.contacts, query),
                )
        }

        /**
         * Begins ensuring a direct conversation with [contact] and emits an
         * [NewChatEvent.OpenConversation] on success. Concurrent taps are
         * coalesced — the first wins, the rest are ignored until it settles.
         */
        fun onPickerContactSelected(contact: User) {
            if (startConversationJob?.isActive == true) return
            startConversationJob =
                viewModelScope.launch {
                    val conversationId =
                        try {
                            messageRepository.ensureConversation(contact.id)
                        } catch (cancellation: kotlinx.coroutines.CancellationException) {
                            throw cancellation
                        } catch (t: Throwable) {
                            _picker.value =
                                _picker.value.copy(
                                    error = t.message ?: "Couldn't start conversation",
                                )
                            return@launch
                        }
                    _events.tryEmit(NewChatEvent.OpenConversation(conversationId))
                    _picker.value = NewChatPickerState()
                }
        }

        /**
         * Loads the synced-contact roster. Source of truth is the local DB
         * (already populated by [ContactRepository.syncContacts]); we kick a
         * server sync in the background so newly registered users surface
         * without forcing the user to re-trigger from the Contacts tab.
         *
         * On sync failure we keep whatever the DB already has and only flag an
         * error if the DB is also empty — that way an offline open of the
         * sheet still shows the cached roster instead of an empty error
         * banner. Mirrors iOS NewChatContactPickerSheet.loadContacts.
         */
        private suspend fun loadContacts() {
            // Best-effort background refresh; don't gate UI on its result.
            launchBackgroundSync()

            try {
                // Take the current snapshot. observeRegisteredContacts is a
                // hot DB observation; the first emission is the current state.
                val contacts = contactRepository.observeRegisteredContacts().first()
                _picker.value =
                    _picker.value.copy(
                        contacts = contacts,
                        filteredContacts = filterContacts(contacts, _picker.value.searchQuery),
                        isLoading = false,
                        error = null,
                    )
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                _picker.value =
                    _picker.value.copy(
                        isLoading = false,
                        error = t.message ?: "Couldn't load contacts",
                    )
            }
        }

        /**
         * Fires `contactRepository.syncContacts()` without blocking the UI on
         * its result. Failure is silent — the local DB is the source of truth
         * for the picker; sync is a freshness optimization, not a hard
         * dependency.
         */
        private fun launchBackgroundSync() {
            viewModelScope.launch {
                try {
                    contactRepository.syncContacts()
                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    // Intentional: see KDoc — picker reads from local DB.
                }
            }
        }

        private fun filterContacts(
            contacts: List<User>,
            query: String,
        ): List<User> {
            val sorted = contacts.sortedBy { it.displayName.lowercase() }
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return sorted
            val needle = trimmed.lowercase()
            return sorted.filter { user ->
                user.displayName.lowercase().contains(needle) ||
                    user.phoneNumber.lowercase().contains(needle) ||
                    (user.bio?.lowercase()?.contains(needle) == true)
            }
        }
    }
