package com.sanchr.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanchr.core.common.DispatcherProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically syncs messages, contacts, and conversation
 * state with the server. Runs on a 15-minute interval when the device has network.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dispatcherProvider: DispatcherProvider,
    // TODO: Inject MessageRepository, ContactRepository for actual sync
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(dispatcherProvider.io) {
        try {
            // Phase 1: Sync pending outbound messages
            syncPendingMessages()

            // Phase 2: Fetch new inbound messages
            fetchNewMessages()

            // Phase 3: Sync conversation metadata
            syncConversations()

            // Phase 4: Delete expired messages
            deleteExpiredMessages()

            // Phase 5: Sync contact registration status
            syncContacts()

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun syncPendingMessages() {
        // TODO: Query local DB for messages with status SENDING
        // TODO: Retry sending them via gRPC
        // TODO: Update status to SENT or FAILED
    }

    private suspend fun fetchNewMessages() {
        // TODO: Call gRPC to fetch messages since last sync timestamp
        // TODO: Decrypt and store in local DB
        // TODO: Update conversation last_message fields
    }

    private suspend fun syncConversations() {
        // TODO: Fetch conversation list from server
        // TODO: Merge with local state (unread counts, pinned, etc.)
    }

    private suspend fun deleteExpiredMessages() {
        // TODO: Delete messages where expires_at < now
    }

    private suspend fun syncContacts() {
        // TODO: Periodically re-check which contacts are registered
    }

    companion object {
        private const val WORK_NAME = "sanchr_periodic_sync"
        private const val MAX_RETRIES = 3

        /**
         * Enqueues the periodic sync worker. Should be called once during app startup.
         */
        fun enqueue(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS,
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Cancels the periodic sync worker.
         */
        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}
