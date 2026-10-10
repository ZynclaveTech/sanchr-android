package com.sanchr.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.crypto.store.SanchrPreKeyStore
import com.sanchr.core.crypto.store.SanchrSignedPreKeyStore
import com.sanchr.core.crypto.store.SignedPreKeyInfo
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
        private val signedPreKeys: SanchrSignedPreKeyStore,
        private val preKeys: SanchrPreKeyStore,
    ) : ViewModel() {
        private val _fingerprintBlocks = MutableStateFlow<List<String>>(emptyList())
        val fingerprintBlocks: StateFlow<List<String>> = _fingerprintBlocks.asStateFlow()

        /**
         * The signed pre-key and one-time pre-key state this device actually
         * holds. Null and -1 are the honest answers when they cannot be read,
         * so the screen says so rather than printing a plausible number.
         */
        private val _keyState = MutableStateFlow(KeyState())
        val keyState: StateFlow<KeyState> = _keyState.asStateFlow()

        init {
            viewModelScope.launch {
                _fingerprintBlocks.value = runCatching { safetyNumbers.localIdentityFingerprintBlocks() }.getOrDefault(emptyList())
                _keyState.value =
                    KeyState(
                        signedPreKey = runCatching { signedPreKeys.newestSignedPreKey() }.getOrNull(),
                        remainingPreKeys = runCatching { preKeys.remainingPreKeyCount() }.getOrDefault(UNKNOWN_COUNT),
                        loaded = true,
                    )
            }
        }

        data class KeyState(
            val signedPreKey: SignedPreKeyInfo? = null,
            val remainingPreKeys: Int = UNKNOWN_COUNT,
            val loaded: Boolean = false,
        ) {
            /** A signed pre-key exists, so sessions can be established with us. */
            val isActive: Boolean get() = signedPreKey != null
        }

        companion object {
            const val UNKNOWN_COUNT = -1
        }
    }
