package com.sanchr.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.sanchr.core.common.Result
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.ConversationType
import com.sanchr.core.model.User
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.ObserveConversationsUseCase
import com.sanchr.domain.messaging.SendAttachmentUseCase
import com.sanchr.feature.chats.media.AttachmentPreparer
import com.sanchr.sync.SyncState
import com.sanchr.sync.SyncWorker
import com.sanchr.sync.realtime.RealtimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The chip bar's filters, the same three iOS offers and in the same order.
 */
enum class ChatFilter(
    val label: String,
) {
    ALL("All"),
    UNREAD("Unread"),
    GROUPS("Groups"),
}

/**
 * How the list orders rows, the same three iOS offers.
 *
 * Pinned always leads regardless: pinning is the user saying "keep this one
 * where I can see it", and a sort that buried a pinned chat would be ignoring
 * that.
 */
enum class ChatSortOrder(
    val label: String,
) {
    RECENT("Most recent"),
    UNREAD_FIRST("Unread first"),
    NAME("Name"),
}

/** The four live controls, bundled because `combine` types only five flows. */
private data class ListControls(
    val refreshing: Boolean,
    val syncing: Boolean,
    val filter: ChatFilter,
    val sort: ChatSortOrder,
)

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
        /** How many chats are archived; the list shows an "Archived" row when non-zero. */
        val archivedCount: Int = 0,
        /** Which chip is lit. */
        val selectedFilter: ChatFilter = ChatFilter.ALL,
    ) : ChatsListUiState {
        /**
         * Pinned chats, which lead the list under their own heading as on iOS.
         *
         * Split here rather than in the composable so the ordering is a
         * property of the state and can be asserted without mounting Compose.
         */
        val pinned: List<Conversation> get() = conversations.filter { it.isPinned }

        /** Everything else, under "ALL CHATS". */
        val unpinned: List<Conversation> get() = conversations.filterNot { it.isPinned }
    }

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

    /** Something the user asked for did not happen, and they should hear so. */
    data class Failed(
        val message: String,
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
        private val sendAttachmentUseCase: SendAttachmentUseCase,
        val syncState: SyncState,
        private val sessionManager: SessionManager,
        realtimeManager: RealtimeManager,
    ) : ViewModel() {
        private val _searchQuery = MutableStateFlow("")
        private val _selectedFilter = MutableStateFlow(ChatFilter.ALL)
        private val _sortOrder = MutableStateFlow(ChatSortOrder.RECENT)

        /** The chosen ordering, for the control in the search field. */
        val sortOrder: StateFlow<ChatSortOrder> = _sortOrder.asStateFlow()

        /**
         * The lit chip, exposed on its own rather than only inside
         * [ChatsListUiState.Success].
         *
         * The chip bar is part of the screen's furniture, not of the list: it
         * shows above an empty account too, as on iOS. Reading the filter out
         * of the success state hid the chips exactly when someone has no chats
         * and is most likely to be looking around the screen.
         */
        val selectedFilter: StateFlow<ChatFilter> = _selectedFilter.asStateFlow()
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

        /** Archived chats, for the Archived screen and the count on the list. */
        val archived: StateFlow<List<Conversation>> =
            messageRepository
                .observeArchivedConversations()
                .catch { emit(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /**
         * Hidden chats, for the Hidden screen and the row that opens it.
         *
         * Kept out of [uiState] deliberately: that combine already takes its
         * maximum arity, and the screen needs this list either way.
         */
        val hidden: StateFlow<List<Conversation>> =
            messageRepository
                .observeHiddenConversations()
                .catch { emit(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val uiState: StateFlow<ChatsListUiState> =
            combine(
                observeConversationsUseCase(),
                _searchQuery,
                combine(_isRefreshing, syncState.isSyncing, _selectedFilter, _sortOrder) { r, s, f, o ->
                    ListControls(refreshing = r, syncing = s, filter = f, sort = o)
                },
                realtimeManager.typingCache,
                archived,
            ) { result, query, controls, typing, archivedList ->
                val refreshing = controls.refreshing
                val syncing = controls.syncing
                val filter = controls.filter
                when (result) {
                    is Result.Loading -> ChatsListUiState.Loading

                    is Result.Success -> {
                        val searched =
                            if (query.isBlank()) {
                                result.data
                            } else {
                                result.data.filter { conversation ->
                                    conversation.title?.contains(query, ignoreCase = true) == true
                                }
                            }
                        val filtered =
                            when (filter) {
                                ChatFilter.ALL -> searched
                                ChatFilter.UNREAD -> searched.filter { it.unreadCount > 0 }
                                ChatFilter.GROUPS -> searched.filter { it.type == ConversationType.GROUP }
                            }
                        val ordered =
                            when (controls.sort) {
                                ChatSortOrder.RECENT -> filtered.sortedByDescending { it.updatedAt }
                                ChatSortOrder.UNREAD_FIRST ->
                                    filtered.sortedWith(
                                        compareByDescending<Conversation> { it.unreadCount > 0 }
                                            .thenByDescending { it.updatedAt },
                                    )
                                ChatSortOrder.NAME ->
                                    filtered.sortedBy { it.title?.lowercase() ?: "" }
                            }
                        // Empty only counts as "nothing here at all" when no
                        // search and no filter are narrowing the list. A chip
                        // that matches nothing is a filtered list with no rows,
                        // not an account with no chats — showing the onboarding
                        // empty state there tells the user their chats are gone.
                        if (filtered.isEmpty() && query.isBlank() && filter == ChatFilter.ALL) {
                            ChatsListUiState.Empty
                        } else {
                            ChatsListUiState.Success(
                                conversations = ordered,
                                searchQuery = query,
                                isRefreshing = refreshing,
                                isSyncing = syncing,
                                currentUserId = sessionManager.getUserId().orEmpty(),
                                typingConversationIds = typing.filterValues { it.isTyping }.keys,
                                archivedCount = archivedList.size,
                                selectedFilter = filter,
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

        private val _actionError = MutableStateFlow<String?>(null)
        val actionError: StateFlow<String?> = _actionError.asStateFlow()

        fun dismissActionError() {
            _actionError.value = null
        }

        // --- Row actions (iOS swipe actions): each persists first, then the list re-emits from the DB ---

        fun togglePin(conversation: Conversation) = action { messageRepository.setPinned(conversation.id, !conversation.isPinned) }

        fun toggleMute(conversation: Conversation) = action { messageRepository.setMuted(conversation.id, !conversation.isMuted) }

        fun setArchived(
            conversation: Conversation,
            archived: Boolean,
        ) = action { messageRepository.setArchived(conversation.id, archived) }

        /**
         * Hides a chat from every list on this device, or restores it.
         *
         * Nothing is deleted and the server is not told, so restoring brings
         * the conversation back with its transcript intact.
         */
        fun setHidden(
            conversation: Conversation,
            hidden: Boolean,
        ) = action { messageRepository.setHidden(conversation.id, hidden) }

        fun markAsRead(conversation: Conversation) = action { messageRepository.markAsRead(conversation.id) }

        fun deleteConversation(conversation: Conversation) = action { messageRepository.deleteConversation(conversation.id) }

        private fun action(block: suspend () -> Unit) {
            viewModelScope.launch {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _actionError.value = e.message ?: "Something went wrong"
                }
            }
        }

        /**
         * Sends a photo taken from the chats list into [conversationId].
         *
         * Read and prepared off the main thread, through the same bounded
         * decoder the editor uses: a capture from a modern phone camera is
         * over 100 megapixels and decoding it whole ends the process.
         */
        fun sendCapturedPhoto(
            context: android.content.Context,
            uri: android.net.Uri,
            conversationId: String,
        ) {
            viewModelScope.launch {
                val prepared =
                    withContext(Dispatchers.IO) {
                        AttachmentPreparer.prepare(context, uri)
                    }
                if (prepared == null) {
                    _events.emit(NewChatEvent.Failed("Could not read that photo"))
                    return@launch
                }
                val result = sendAttachmentUseCase(conversationId, prepared, null) {}
                if (result is Result.Error) {
                    _events.emit(NewChatEvent.Failed(result.exception.message ?: "Could not send the photo"))
                }
            }
        }

        /** Chooses the ordering. */
        fun onSortOrderSelected(order: ChatSortOrder) {
            _sortOrder.value = order
        }

        /** Lights a chip and narrows the list to it. */

        fun onFilterSelected(filter: ChatFilter) {
            _selectedFilter.value = filter
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
