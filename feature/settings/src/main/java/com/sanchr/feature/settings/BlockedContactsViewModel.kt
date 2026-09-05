package com.sanchr.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.domain.contacts.BlockedContact
import com.sanchr.domain.contacts.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlockedContactsUiState(
    val isLoading: Boolean = true,
    val blocked: List<BlockedContact> = emptyList(),
    val error: String? = null,
)

/** The Privacy › Blocked Contacts screen (iOS `BlockedContactsView`): the server's list, with Unblock. */
@HiltViewModel
class BlockedContactsViewModel
    @Inject
    constructor(
        private val contactRepository: ContactRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(BlockedContactsUiState())
        val uiState: StateFlow<BlockedContactsUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                try {
                    val blocked = contactRepository.blockedContacts()
                    _uiState.update { it.copy(isLoading = false, blocked = blocked) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load blocked contacts") }
                }
            }
        }

        fun unblock(userId: String) {
            viewModelScope.launch {
                try {
                    contactRepository.setBlocked(userId, blocked = false)
                    _uiState.update { state -> state.copy(blocked = state.blocked.filterNot { it.userId == userId }) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.message ?: "Could not unblock") }
                }
            }
        }

        fun dismissError() {
            _uiState.update { it.copy(error = null) }
        }
    }
