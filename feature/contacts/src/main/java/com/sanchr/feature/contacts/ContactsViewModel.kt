package com.sanchr.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.common.Result
import com.sanchr.core.model.User
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.domain.contacts.SyncContactsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactsUiState(
    val registeredContacts: List<User> = emptyList(),
    val filteredContacts: List<User> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val syncContactsUseCase: SyncContactsUseCase,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _syncState = MutableStateFlow(false)

    val uiState: StateFlow<ContactsUiState> = combine(
        contactRepository.observeRegisteredContacts(),
        _searchQuery,
        _syncState,
    ) { contacts, query, isSyncing ->
        val filtered = if (query.isBlank()) contacts
        else contacts.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.phoneNumber.contains(query)
        }
        ContactsUiState(
            registeredContacts = contacts,
            filteredContacts = filtered,
            searchQuery = query,
            isLoading = false,
            isSyncing = isSyncing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ContactsUiState(),
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun syncContacts() {
        viewModelScope.launch {
            _syncState.value = true
            when (val result = syncContactsUseCase()) {
                is Result.Error -> {
                    // TODO: Show error via UI state
                }
                else -> { /* Success or Loading */ }
            }
            _syncState.value = false
        }
    }
}
