package com.sanchr.feature.profile.verify

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.crypto.verify.SafetyNumber
import com.sanchr.core.crypto.verify.SafetyNumberManager
import com.sanchr.core.crypto.verify.ScanOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the verify screen is showing right now. */
data class VerifySafetyNumberUiState(
    val isLoading: Boolean = true,
    val safetyNumber: SafetyNumber? = null,
    /**
     * Why the code could not be computed. Non-null means the screen shows the
     * reason and nothing else: never a placeholder number, which would let
     * someone "verify" against digits that came from no key at all.
     */
    val error: String? = null,
    val scannerOpen: Boolean = false,
    /** The result of the last scan, until the user dismisses it. */
    val scanOutcome: ScanOutcome? = null,
) {
    /** Whether the local user has marked this identity verified. */
    val isVerified: Boolean get() = safetyNumber?.verifiedAtMillis != null
}

/**
 * Drives one contact's safety-number screen: computes the code, compares a
 * scan against it, and records or revokes the verification.
 */
@HiltViewModel
class VerifySafetyNumberViewModel
    @Inject
    constructor(
        private val safetyNumbers: SafetyNumberManager,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val userId: String = savedStateHandle["userId"] ?: ""

        private val _uiState = MutableStateFlow(VerifySafetyNumberUiState())
        val uiState: StateFlow<VerifySafetyNumberUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                runCatching { safetyNumbers.safetyNumber(userId) }
                    .onSuccess { number -> _uiState.update { it.copy(isLoading = false, safetyNumber = number, error = null) } }
                    .onFailure {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                safetyNumber = null,
                                error = "Couldn't compute this contact's security code. Exchange messages first, then try again.",
                            )
                        }
                    }
            }
        }

        fun openScanner() = _uiState.update { it.copy(scannerOpen = true, scanOutcome = null) }

        fun closeScanner() = _uiState.update { it.copy(scannerOpen = false) }

        /** Dismisses the result banner without changing the verification. */
        fun dismissScanOutcome() = _uiState.update { it.copy(scanOutcome = null) }

        /**
         * Compares a scanned payload with this conversation's fingerprint. A
         * match records the verification; a mismatch revokes any earlier one,
         * because the identity on the other phone is not the one that was
         * checked before.
         */
        fun onScanned(payload: ByteArray) {
            viewModelScope.launch {
                val outcome = runCatching { safetyNumbers.compare(userId, payload) }.getOrDefault(ScanOutcome.Unreadable)
                when (outcome) {
                    ScanOutcome.Match -> safetyNumbers.markVerified(userId)
                    ScanOutcome.Mismatch -> safetyNumbers.clearVerified(userId)
                    ScanOutcome.Unreadable -> Unit
                }
                _uiState.update { it.copy(scannerOpen = false, scanOutcome = outcome) }
                if (outcome != ScanOutcome.Unreadable) load()
            }
        }

        /** Marks verified after the two people read the digits aloud instead of scanning. */
        fun markVerifiedManually() {
            viewModelScope.launch {
                safetyNumbers.markVerified(userId)
                load()
            }
        }

        /** Clears the verification at the user's request. */
        fun clearVerification() {
            viewModelScope.launch {
                safetyNumbers.clearVerified(userId)
                load()
            }
        }
    }
