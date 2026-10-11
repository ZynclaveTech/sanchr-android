package com.sanchr.feature.contacts.scan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.designsystem.component.QrCodes
import com.sanchr.domain.messaging.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the scanner screen is doing. */
sealed interface ScanContactState {
    data object Scanning : ScanContactState

    /** A Sanchr code was read; its conversation is being opened. */
    data object Opening : ScanContactState

    data class Problem(
        val message: String,
    ) : ScanContactState
}

/**
 * Starts a chat with whoever's profile code is scanned.
 *
 * Every profile has shown a QR code for a while, and this app could draw one
 * and not read one: the scanner existed, and was wired only to comparing
 * security codes. iOS scans a profile code and opens the chat.
 */
@HiltViewModel
class ScanContactViewModel
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<ScanContactState>(ScanContactState.Scanning)
        val state: StateFlow<ScanContactState> = _state.asStateFlow()

        private val _opened = MutableSharedFlow<String>()

        /** Conversation ids to show, one per successful scan. */
        val opened = _opened.asSharedFlow()

        /**
         * Called for every frame the camera reads a code in — many times a
         * second for as long as one is in view. Only the first is acted on;
         * the rest arrive while it is being opened, or after it failed.
         */
        fun onScanned(text: String) {
            if (_state.value != ScanContactState.Scanning) return
            val userId = QrCodes.userIdFromProfileLink(text)
            if (userId == null) {
                _state.value = ScanContactState.Problem(NOT_A_SANCHR_CODE)
                return
            }
            _state.value = ScanContactState.Opening
            viewModelScope.launch {
                try {
                    _opened.emit(messageRepository.ensureConversation(userId))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "could not open the scanned contact's chat", e)
                    _state.value = ScanContactState.Problem(COULD_NOT_OPEN)
                }
            }
        }

        /** After a problem: look again. */
        fun scanAgain() {
            if (_state.value is ScanContactState.Problem) _state.value = ScanContactState.Scanning
        }

        fun onCameraRefused() {
            _state.value = ScanContactState.Problem(CAMERA_NEEDED)
        }

        companion object {
            private const val TAG = "ScanContactViewModel"
            const val NOT_A_SANCHR_CODE = "That isn't a Sanchr contact code."
            const val COULD_NOT_OPEN = "Couldn't open that chat. Check your connection and try again."
            const val CAMERA_NEEDED = "Camera access is needed to scan a code."
        }
    }
