package com.sanchr.sync

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
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.domain.messaging.SendMessageUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext

/**
 * Periodic (and on-demand) retry worker for QUEUED outbound messages.
 *
 * [SendMessageUseCase] leaves a row in QUEUED after a retryable failure
 * and stamps `last_attempt_at`; this worker periodically re-picks those
 * rows off [MessageDao.getRetryableQueued] and runs another attempt
 * through the use case. Rows that have already burned all 3 attempts
 * are in FAILED and ignored here — terminal failure is a user-driven
 * retry (re-send from the UI).
 *
 * **Cadence.** WorkManager's minimum periodic interval is 15 minutes, so
 * the periodic schedule is a worst-case floor. The send path *also*
 * enqueues a one-time SendRetryWorker after each failed attempt so a
 * transient blip (flaky cell, brief DNS hiccup) is retried on the order
 * of seconds, not quarter-hours.
 *
 * **Backoff.** Within a single run, each row is gated by
 * [MessageDao.getRetryableQueued]'s `minBackoffMillis` parameter — the
 * per-row floor between attempts. Cross-run backoff is implicit in the
 * WorkManager periodic cadence.
 */
@HiltWorker
class SendRetryWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val messageDao: MessageDao,
        private val sendMessageUseCase: SendMessageUseCase,
        private val dispatchers: DispatcherProvider,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            withContext(dispatchers.io) {
                try {
                    val now = System.currentTimeMillis()
                    val retryable =
                        messageDao.getRetryableQueued(
                            now = now,
                            maxAttempts = MAX_ATTEMPTS,
                            minBackoffMillis = MIN_BACKOFF_MS,
                            limit = BATCH_LIMIT,
                        )

                    if (retryable.isEmpty()) {
                        Log.d(TAG, "no retryable queued messages")
                        return@withContext Result.success()
                    }

                    var succeeded = 0
                    var stillFailed = 0
                    for (entity in retryable) {
                        val result = sendMessageUseCase.attemptSend(entity)
                        if (result is com.sanchr.core.common.Result.Success) {
                            succeeded += 1
                        } else {
                            stillFailed += 1
                        }
                    }
                    Log.d(
                        TAG,
                        "retry batch processed: total=${retryable.size} ok=$succeeded fail=$stillFailed",
                    )
                    Result.success()
                } catch (e: Exception) {
                    Log.w(TAG, "retry batch aborted", e)
                    if (runAttemptCount < WORK_MAX_RETRIES) Result.retry() else Result.failure()
                }
            }

        companion object {
            private const val TAG = "SendRetryWorker"
            const val PERIODIC_WORK_NAME = "sanchr_send_retry"
            const val ONE_TIME_WORK_NAME = "sanchr_send_retry_once"

            /** Must match [SendMessageUseCase.MAX_ATTEMPTS]. */
            private const val MAX_ATTEMPTS = 3

            /**
             * Per-row floor between two attempts of the same message.
             * 30 seconds gives network transients time to clear without
             * making the UI feel stuck.
             */
            private const val MIN_BACKOFF_MS = 30_000L

            /** Max rows processed per worker run. Caps worker wall-time. */
            private const val BATCH_LIMIT = 50

            /** Ceiling on WorkManager's own failure-retry of this worker. */
            private const val WORK_MAX_RETRIES = 3

            /**
             * Schedule the periodic retry sweep. Idempotent — uses
             * [ExistingPeriodicWorkPolicy.KEEP] so calling this on every
             * app start does not reset the timer.
             */
            fun schedulePeriodic(workManager: WorkManager) {
                val constraints =
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                // 15 minutes is WorkManager's periodic floor. Combined with
                // [enqueueOneTime] fired from the send path, effective
                // retry latency for a transient failure is seconds, not
                // minutes — the periodic schedule is the safety net.
                val request =
                    PeriodicWorkRequestBuilder<SendRetryWorker>(15, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .build()

                workManager.enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }

            /**
             * Fire a one-shot expedited retry. Called by the send path
             * after a transient failure so retries happen on the order of
             * seconds. [ExistingWorkPolicy.KEEP] collapses a burst of
             * failures onto a single in-flight retry pass.
             */
            fun enqueueOneTime(context: Context) {
                val constraints =
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                val request =
                    OneTimeWorkRequestBuilder<SendRetryWorker>()
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
