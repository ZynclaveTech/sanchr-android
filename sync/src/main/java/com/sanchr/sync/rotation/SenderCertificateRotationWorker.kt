package com.sanchr.sync.rotation

import android.content.Context
import android.util.Log
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
import com.sanchr.core.crypto.sealed.SenderCertificateManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext

/**
 * Periodic worker that refreshes the sealed-sender sender certificate.
 * Runs every 24h; [SenderCertificateManager.refresh] fetches a fresh
 * certificate from the auth service and caches it for outbound sealed
 * envelopes.
 *
 * Part of the M3 discrete-rotation-worker split (Phase E).
 */
@HiltWorker
class SenderCertificateRotationWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val senderCertificateManager: SenderCertificateManager,
        private val dispatchers: DispatcherProvider,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            withContext(dispatchers.io) {
                runCatching { senderCertificateManager.refresh() }
                    .fold(
                        onSuccess = { Result.success() },
                        onFailure = {
                            Log.w(TAG, "failed", it)
                            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                        },
                    )
            }

        companion object {
            private const val TAG = "SenderCertRotation"
            const val UNIQUE_NAME = "sender-certificate-rotation"
            private const val MAX_RETRIES = 3

            fun schedulePeriodic(workManager: WorkManager) {
                val constraints =
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                val request =
                    PeriodicWorkRequestBuilder<SenderCertificateRotationWorker>(24, TimeUnit.HOURS)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .build()

                workManager.enqueueUniquePeriodicWork(
                    UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            }
        }
    }
