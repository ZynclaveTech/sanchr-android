package com.sanchr.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.crypto.verify.SafetyNumberManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Supplies the one real, account-level value the Encryption Keys screen can
 * show: this device's own identity key fingerprint.
 *
 * The screen used to print twelve hardcoded hex blocks, which looked exactly
 * like a fingerprint and belonged to nobody. Empty is the honest state when
 * the key cannot be read, so the screen says so instead of inventing digits.
 */
@HiltViewModel
class EncryptionKeysViewModel
    @Inject
    constructor(
        private val safetyNumbers: SafetyNumberManager,
    ) : ViewModel() {
        private val _fingerprintBlocks = MutableStateFlow<List<String>>(emptyList())
        val fingerprintBlocks: StateFlow<List<String>> = _fingerprintBlocks.asStateFlow()

        init {
            viewModelScope.launch {
                _fingerprintBlocks.value = runCatching { safetyNumbers.localIdentityFingerprintBlocks() }.getOrDefault(emptyList())
            }
        }
    }
