package com.sanchr.feature.contacts

import android.content.ContentResolver
import android.provider.ContactsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.proto.contacts.BlockContactRequest
import com.sanchr.proto.contacts.Contact
import com.sanchr.proto.contacts.ContactServiceClient
import com.sanchr.proto.contacts.GetContactsRequest
import com.sanchr.proto.contacts.SyncContactsRequest
import com.sanchr.proto.contacts.UnblockContactRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ContactsUiState {
    data object Loading : ContactsUiState

    data class Success(
        val groupedContacts: Map<Char, List<Contact>>,
        val onlineUserIds: Set<String>,
        val searchQuery: String,
    ) : ContactsUiState

    data object Empty : ContactsUiState

    data class Error(
        val message: String,
    ) : ContactsUiState
}

data class ContactSyncUiState(
    val isSyncing: Boolean = false,
    val syncComplete: Boolean = false,
    val matchedCount: Int = 0,
    val progress: Float = 0f,
    val errorMessage: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ContactsViewModel
    @Inject
    constructor(
        private val contactServiceClient: ContactServiceClient,
    ) : ViewModel() {
        companion object {
            private const val TAG = "ContactsViewModel"
            private const val PAGE_SIZE = 50
        }

        private val _allContacts = MutableStateFlow<List<Contact>>(emptyList())
        private val _searchQuery = MutableStateFlow("")
        private val _onlineUserIds = MutableStateFlow<Set<String>>(emptySet())
        private val _isLoading = MutableStateFlow(true)
        private val _errorMessage = MutableStateFlow<String?>(null)
        private val _isRefreshing = MutableStateFlow(false)

        private val _syncState = MutableStateFlow(ContactSyncUiState())
        val syncState: StateFlow<ContactSyncUiState> = _syncState

        private val _events = MutableSharedFlow<ContactsEvent>()
        val events = _events.asSharedFlow()

        val searchQuery: StateFlow<String> = _searchQuery

        val uiState: StateFlow<ContactsUiState> =
            combine(
                _allContacts,
                _searchQuery.debounce(300),
                _onlineUserIds,
                _isLoading,
                _errorMessage,
            ) { contacts, query, onlineIds, loading, error ->
                when {
                    error != null -> ContactsUiState.Error(error)
                    loading -> ContactsUiState.Loading
                    contacts.isEmpty() -> ContactsUiState.Empty
                    else -> {
                        val filtered =
                            if (query.isBlank()) {
                                contacts
                            } else {
                                contacts.filter {
                                    it.displayName.contains(query, ignoreCase = true) ||
                                        it.phoneNumber.contains(query)
                                }
                            }
                        if (filtered.isEmpty()) {
                            ContactsUiState.Empty
                        } else {
                            val grouped =
                                filtered
                                    .sortedBy { it.displayName.lowercase() }
                                    .groupBy {
                                        val first = it.displayName.firstOrNull()?.uppercaseChar() ?: '#'
                                        if (first.isLetter()) first else '#'
                                    }.toSortedMap()
                            ContactsUiState.Success(
                                groupedContacts = grouped,
                                onlineUserIds = onlineIds,
                                searchQuery = query,
                            )
                        }
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ContactsUiState.Loading,
            )

        init {
            loadContacts()
        }

        fun onSearchQueryChanged(query: String) {
            _searchQuery.value = query
        }

        fun refresh() {
            _isRefreshing.value = true
            loadContacts()
        }

        private fun loadContacts(pageToken: String = "") {
            viewModelScope.launch {
                try {
                    _isLoading.value = pageToken.isEmpty() && _allContacts.value.isEmpty()
                    _errorMessage.value = null

                    val response =
                        contactServiceClient.getContacts(
                            GetContactsRequest(
                                pageToken = pageToken,
                                pageSize = PAGE_SIZE,
                            ),
                        )

                    if (pageToken.isEmpty()) {
                        _allContacts.value = response.contacts
                    } else {
                        _allContacts.update { current -> current + response.contacts }
                    }

                    // Track online users
                    val onlineIds =
                        response.contacts
                            .filter { it.isOnline }
                            .map { it.userId }
                            .toSet()
                    _onlineUserIds.update { current -> current + onlineIds }

                    // Load next page if available
                    if (response.nextPageToken.isNotEmpty()) {
                        loadContacts(response.nextPageToken)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load contacts", e)
                    if (_allContacts.value.isEmpty()) {
                        _errorMessage.value = e.message ?: "Failed to load contacts"
                    }
                } finally {
                    _isLoading.value = false
                    _isRefreshing.value = false
                }
            }
        }

        fun blockContact(userId: String) {
            viewModelScope.launch {
                try {
                    val response =
                        contactServiceClient.blockContact(
                            BlockContactRequest(userId = userId),
                        )
                    if (response.success) {
                        _allContacts.update { contacts ->
                            contacts.map {
                                if (it.userId == userId) it.copy(isBlocked = true) else it
                            }
                        }
                        _events.emit(ContactsEvent.ContactBlocked)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to block contact", e)
                    _events.emit(ContactsEvent.Error("Failed to block contact"))
                }
            }
        }

        fun unblockContact(userId: String) {
            viewModelScope.launch {
                try {
                    val response =
                        contactServiceClient.unblockContact(
                            UnblockContactRequest(userId = userId),
                        )
                    if (response.success) {
                        _allContacts.update { contacts ->
                            contacts.map {
                                if (it.userId == userId) it.copy(isBlocked = false) else it
                            }
                        }
                        _events.emit(ContactsEvent.ContactUnblocked)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unblock contact", e)
                    _events.emit(ContactsEvent.Error("Failed to unblock contact"))
                }
            }
        }

        fun syncContacts(contentResolver: ContentResolver) {
            viewModelScope.launch {
                _syncState.value = ContactSyncUiState(isSyncing = true, progress = 0.1f)
                try {
                    val phoneHashes = readAndHashDeviceContacts(contentResolver)
                    _syncState.update { it.copy(progress = 0.5f) }

                    val response =
                        contactServiceClient.syncContacts(
                            SyncContactsRequest(phoneHashes = phoneHashes),
                        )

                    _syncState.value =
                        ContactSyncUiState(
                            isSyncing = false,
                            syncComplete = true,
                            matchedCount = response.matchedContacts.size,
                            progress = 1f,
                        )

                    // Reload contacts list to include newly synced contacts
                    loadContacts()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync contacts", e)
                    _syncState.value =
                        ContactSyncUiState(
                            isSyncing = false,
                            errorMessage = e.message ?: "Sync failed",
                        )
                }
            }
        }

        private fun readAndHashDeviceContacts(contentResolver: ContentResolver): List<String> {
            val phoneNumbers = mutableSetOf<String>()
            val cursor =
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null,
                    null,
                    null,
                )
            cursor?.use {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val number = it.getString(numberIndex)
                    if (!number.isNullOrBlank()) {
                        // Normalize: remove spaces, dashes, parens
                        val normalized = number.replace(Regex("[\\s\\-()]+"), "")
                        phoneNumbers.add(normalized)
                    }
                }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            return phoneNumbers.map { number ->
                val hash = digest.digest(number.toByteArray(Charsets.UTF_8))
                hash.joinToString("") { "%02x".format(it) }
            }
        }
    }

sealed interface ContactsEvent {
    data object ContactBlocked : ContactsEvent

    data object ContactUnblocked : ContactsEvent

    data class Error(
        val message: String,
    ) : ContactsEvent
}
