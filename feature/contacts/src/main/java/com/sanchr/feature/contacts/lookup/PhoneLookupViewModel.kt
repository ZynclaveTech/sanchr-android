package com.sanchr.feature.contacts.lookup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.model.User
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.domain.messaging.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the lookup has found so far. */
sealed interface LookupResult {
    data object Idle : LookupResult

    data object Searching : LookupResult

    data class Found(
        val user: User,
    ) : LookupResult

    data object NotFound : LookupResult

    data class Failed(
        val message: String,
    ) : LookupResult
}

data class PhoneLookupUiState(
    val phoneNumber: String = "",
    val result: LookupResult = LookupResult.Idle,
    val isStartingChat: Boolean = false,
) {
    /**
     * Whether the number is worth sending. Deliberately permissive about
     * format and strict only about emptiness: the server owns what a valid
     * number is, and rejecting locally would block legitimate ones.
     */
    val canSearch: Boolean get() = phoneNumber.filter { it.isDigit() }.length >= MIN_DIGITS && result != LookupResult.Searching

    private companion object {
        const val MIN_DIGITS = 6
    }
}

/**
 * Finds someone by phone number and opens a chat with them.
 *
 * `ContactRepository.lookupByPhone` has existed and gone unused: without this
 * screen, the only way to reach someone was to have already synced them from
 * the address book, so a number typed by hand led nowhere. iOS has had
 * `PhoneNumberLookupView` throughout.
 */
@HiltViewModel
class PhoneLookupViewModel
    @Inject
    constructor(
        private val contactRepository: ContactRepository,
        private val messageRepository: MessageRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(PhoneLookupUiState())
        val uiState: StateFlow<PhoneLookupUiState> = _uiState.asStateFlow()

        fun onPhoneNumberChanged(value: String) = _uiState.update { it.copy(phoneNumber = value, result = LookupResult.Idle) }

        fun search() {
            val number = _uiState.value.phoneNumber.trim()
            if (!_uiState.value.canSearch) return
            _uiState.update { it.copy(result = LookupResult.Searching) }
            viewModelScope.launch {
                val outcome =
                    runCatching { contactRepository.lookupByPhone(number) }
                        .fold(
                            onSuccess = { user -> user?.let(LookupResult::Found) ?: LookupResult.NotFound },
                            // A failed lookup is not the same as "nobody has
                            // this number": telling someone their contact is
                            // not on Sanchr because the network blipped would
                            // be worse than saying the search failed.
                            onFailure = { LookupResult.Failed(it.message ?: "Couldn't search right now") },
                        )
                _uiState.update { it.copy(result = outcome) }
            }
        }

        /** Opens (or creates) the conversation with the found user. */
        fun startChat(onOpened: (conversationId: String) -> Unit) {
            val user = (_uiState.value.result as? LookupResult.Found)?.user ?: return
            if (_uiState.value.isStartingChat) return
            _uiState.update { it.copy(isStartingChat = true) }
            viewModelScope.launch {
                val id = runCatching { messageRepository.ensureConversation(user.id) }.getOrNull()
                _uiState.update { it.copy(isStartingChat = false) }
                if (id != null) {
                    onOpened(id)
                } else {
                    _uiState.update { it.copy(result = LookupResult.Failed("Couldn't open that chat")) }
                }
            }
        }
    }
