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
import com.sanchr.core.crypto.SignalKeyManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext

/**
 * Periodic worker that rotates the Signal signed pre-key if the current one
 * is older than the rotation threshold. Cadence is 24h; the actual rotation
 * decision ( >7d old ) is made inside [SignalKeyManager.rotateSignedPreKeyIfNeeded ],
 * so firing on a 24h cadence is a cheap no-op on days where no rotation is due.
 *
 * Carved out of the legacy [com.sanchr.sync.SyncWorker] as part of the M3
 * discrete-rotation-worker split (Phase E).
 */
@HiltWorker
class SignedPreKeyRotationWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val signalKeyManager: SignalKeyManager,
        private val dispatchers: DispatcherProvider,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            withContext(dispatchers.io) {
                runCatching { signalKeyManager.rotateSignedPreKeyIfNeeded() }
                    .fold(
                        onSuccess = { Result.success() },
                        onFailure = {
                            Log.w(TAG, "failed", it)
                            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                        },
                    )
            }

        companion object {
            private const val TAG = "SignedPreKeyRotation"
            const val UNIQUE_NAME = "signed-prekey-rotation"
            private const val MAX_RETRIES = 3

            fun schedulePeriodic(workManager: WorkManager) {
                val constraints =
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                val request =
                    PeriodicWorkRequestBuilder<SignedPreKeyRotationWorker>(24, TimeUnit.HOURS)
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
