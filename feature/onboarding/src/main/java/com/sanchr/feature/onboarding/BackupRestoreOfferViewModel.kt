package com.sanchr.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.sync.backup.ChatBackupManager
import com.sanchr.sync.backup.RestorableBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the restore offer is in its short life. */
sealed interface BackupRestoreOfferState {
    /** Asking the server whether this account has a backup. */
    data object Checking : BackupRestoreOfferState

    /** Nothing to restore, or the check failed. The caller moves on. */
    data object Nothing : BackupRestoreOfferState

    data class Found(
        val backup: RestorableBackup,
        val recoveryKey: String = "",
        val isRestoring: Boolean = false,
        val error: String? = null,
    ) : BackupRestoreOfferState

    data class Restored(
        val backupAtMillis: Long?,
    ) : BackupRestoreOfferState
}

/**
 * Offers to bring back a reinstalling user's history.
 *
 * Restore existed only in Settings → Chats, which a user reinstalling has no
 * reason to visit: they open the app, find it empty, and conclude their
 * history is gone. iOS has offered this since launch. The history was always
 * there — nothing was lost except the chance to ask for it.
 */
@HiltViewModel
class BackupRestoreOfferViewModel
    @Inject
    constructor(
        private val backupManager: ChatBackupManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow<BackupRestoreOfferState>(BackupRestoreOfferState.Checking)
        val state: StateFlow<BackupRestoreOfferState> = _state.asStateFlow()

        init {
            check()
        }

        private fun check() {
            viewModelScope.launch {
                val found = backupManager.findRestorableBackup()
                _state.value =
                    if (found == null) {
                        BackupRestoreOfferState.Nothing
                    } else {
                        BackupRestoreOfferState.Found(found)
                    }
            }
        }

        fun onRecoveryKeyChanged(value: String) {
            _state.update { current ->
                if (current is BackupRestoreOfferState.Found) current.copy(recoveryKey = value, error = null) else current
            }
        }

        fun restore() {
            val current = _state.value as? BackupRestoreOfferState.Found ?: return
            if (current.isRestoring) return
            _state.value = current.copy(isRestoring = true, error = null)
            viewModelScope.launch {
                val outcome =
                    runCatching {
                        backupManager.restoreLatestBackup(current.recoveryKey.trim().takeIf { it.isNotEmpty() })
                    }
                _state.value =
                    outcome.fold(
                        onSuccess = { BackupRestoreOfferState.Restored(it.backupAtMillis) },
                        onFailure = { failure ->
                            // The key stays in the field: a wrong key is the
                            // likely cause, and clearing it would make the
                            // user retype all of it to fix one character.
                            current.copy(
                                isRestoring = false,
                                error = failure.message ?: "Could not restore this backup.",
                            )
                        },
                    )
            }
        }
    }
