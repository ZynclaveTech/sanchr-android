package com.sanchr.sync.rotation

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
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
 * Worker that replenishes Signal one-time pre-keys when the server-side
 * stock falls below threshold. Fires both on a 24h periodic safety net
 * and as an expedited one-shot whenever the server pushes a
 * [com.sanchr.proto.messaging.ServerEvent.PreKeyCountLow] over the
 * realtime stream.
 *
 * Part of the M3 discrete-rotation-worker split (Phase E). The body is
 * a thin wrapper around [SignalKeyManager.checkAndReplenishPreKeys], which
 * contains the threshold-check and upload logic.
 */
@HiltWorker
class PreKeyReplenishWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val signalKeyManager: SignalKeyManager,
        private val dispatchers: DispatcherProvider,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            withContext(dispatchers.io) {
                runCatching { signalKeyManager.checkAndReplenishPreKeys() }
                    .fold(
                        onSuccess = { Result.success() },
                        onFailure = {
                            Log.w(TAG, "failed", it)
                            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                        },
                    )
            }

        companion object {
            private const val TAG = "PreKeyReplenish"
            const val UNIQUE_NAME = "prekey-replenish"
            const val ONE_TIME_WORK_NAME = "prekey-replenish-once"
            private const val MAX_RETRIES = 3

            fun schedulePeriodic(workManager: WorkManager) {
                val constraints =
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                val request =
                    PeriodicWorkRequestBuilder<PreKeyReplenishWorker>(24, TimeUnit.HOURS)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .build()

                workManager.enqueueUniquePeriodicWork(
                    UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            }

            /**
             * Fire a one-shot expedited replenish. Called from
             * [com.sanchr.sync.realtime.RealtimeManager] when the server
             * pushes PreKeyCountLow. [ExistingWorkPolicy.KEEP] collapses
             * a rapid burst of events onto a single in-flight run.
             */
            fun enqueueOneTime(context: Context) {
                val constraints =
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                val request =
                    OneTimeWorkRequestBuilder<PreKeyReplenishWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .build()

                WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.KEEP, request)
            }
        }
    }
