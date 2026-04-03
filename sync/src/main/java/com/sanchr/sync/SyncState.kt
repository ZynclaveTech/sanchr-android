package com.sanchr.sync

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observable sync state that UI layers can collect to display sync status
 * indicators (e.g., "Syncing..." banner, error messages, badge counts).
 *
 * This is a singleton shared between [SyncWorker] (producer) and ViewModels
 * (consumers). All mutations happen on the worker thread; reads are safe from
 * any thread via [StateFlow].
 */
@Singleton
class SyncState @Inject constructor() {

    companion object {
        private const val TAG = "SyncState"

        /** Sync is considered stale after 5 minutes. */
        private const val STALE_THRESHOLD_MS = 5 * 60 * 1000L
    }

    private val _lastSyncTimestamp = MutableStateFlow<Long?>(null)

    /** Epoch millis of the last successful sync, or null if never synced. */
    val lastSyncTimestamp: StateFlow<Long?> = _lastSyncTimestamp.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)

    /** True while a sync operation is actively running. */
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _pendingMessageCount = MutableStateFlow(0)

    /** Number of new messages received in the most recent sync. */
    val pendingMessageCount: StateFlow<Int> = _pendingMessageCount.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)

    /** Human-readable error from the last failed sync, or null if last sync succeeded. */
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    /**
     * Returns true if the data is stale and a sync should be triggered.
     * Data is considered stale if:
     * - We have never synced, OR
     * - More than [STALE_THRESHOLD_MS] has elapsed since the last sync
     */
    val needsSync: Boolean
        get() {
            val last = _lastSyncTimestamp.value ?: return true
            return System.currentTimeMillis() - last > STALE_THRESHOLD_MS
        }

    /**
     * Called by [SyncWorker] when a sync operation begins.
     */
    fun markSyncStarted() {
        _isSyncing.value = true
        _syncError.value = null
        Log.d(TAG, "Sync started")
    }

    /**
     * Called by [SyncWorker] when a sync operation completes successfully.
     *
     * @param messageCount Number of new messages fetched during this sync.
     */
    fun markSyncCompleted(messageCount: Int) {
        _isSyncing.value = false
        _lastSyncTimestamp.value = System.currentTimeMillis()
        _pendingMessageCount.value = messageCount
        _syncError.value = null
        Log.d(TAG, "Sync completed (messages=$messageCount)")
    }

    /**
     * Called by [SyncWorker] when a sync operation fails.
     *
     * @param error Human-readable error description.
     */
    fun markSyncFailed(error: String) {
        _isSyncing.value = false
        _syncError.value = error
        Log.w(TAG, "Sync failed: $error")
    }
}
