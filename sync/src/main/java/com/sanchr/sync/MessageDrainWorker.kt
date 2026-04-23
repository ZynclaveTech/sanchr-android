package com.sanchr.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.domain.messaging.EnvelopeDecryptResult
import com.sanchr.domain.messaging.EnvelopeKind
import com.sanchr.domain.messaging.IncomingEnvelopeContext
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.ReceiveMessageUseCase
import com.sanchr.domain.messaging.ServerProvidedSender
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.SyncRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Expedited drain triggered by an FCM wake push.
 *
 * The push itself carries no content (see [com.sanchr.core.notifications.PushPayload]).
 * This worker is the only code path that actually reads pending envelopes from
 * the server on the FCM path — it calls the `SyncMessages` server-streaming
 * RPC, funnels each envelope through [ReceiveMessageUseCase] (which decrypts
 * and persists the [com.sanchr.core.database.entity.MessageEntity]), then acks
 * the batch via `AckMessages`.
 *
 * Retries up to 3 times with WorkManager's built-in back-off; after that the
 * next FCM wake — or the next periodic [SyncWorker] — will pick up the
 * envelopes again since unacked messages remain on the server queue.
 */
@HiltWorker
class MessageDrainWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val messagingClient: MessagingServiceClient,
        private val receiveMessageUseCase: ReceiveMessageUseCase,
        private val messageRepository: MessageRepository,
        private val dispatchers: DispatcherProvider,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            withContext(dispatchers.io) {
                try {
                    val drained = withTimeoutOrNull(DRAIN_TIMEOUT_MS) { drain() } ?: 0
                    Log.d(TAG, "drain completed, envelopes=$drained")
                    Result.success()
                } catch (e: Exception) {
                    Log.w(TAG, "drain failed", e)
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                }
            }

        /**
         * Pulls every pending envelope via `SyncMessages`, decrypts + persists
         * each through [ReceiveMessageUseCase], then flushes acks for the
         * successfully-decrypted rows. Returns the number of envelopes the
         * server delivered (not the number persisted — duplicates / quarantines
         * still count toward "we drained the queue").
         */
        private suspend fun drain(): Int {
            val envelopes =
                messagingClient
                    .syncMessages(SyncRequest(sinceTimestamp = 0L))
                    .toList()

            if (envelopes.isEmpty()) {
                messageRepository.flushPendingAcks()
                return 0
            }

            for (envelope in envelopes) {
                val senderDeviceId = envelope.senderDevice.takeIf { it > 0 } ?: 1
                val result =
                    receiveMessageUseCase.receive(
                        envelopeBytes = envelope.cipherText,
                        // Server does not tag sealed vs non-sealed on SyncMessages today;
                        // the FCM wake path mirrors SyncWorker's existing NON_SEALED
                        // treatment. Sealed delivery rides a separate ServerEvent
                        // channel (SealedInboundMessage) and is not funneled here.
                        kind = EnvelopeKind.NON_SEALED,
                        serverTimestamp = envelope.serverTimestamp,
                        declaredSender = ServerProvidedSender(envelope.senderId, senderDeviceId),
                        envelopeContext =
                            IncomingEnvelopeContext(
                                conversationId = envelope.conversationId,
                                messageId = envelope.messageId,
                                contentType = envelope.contentType,
                            ),
                        // Defer ack RPC until after the drain loop so a burst of N
                        // envelopes produces one batched AckMessages call, not N.
                        flushAckImmediately = false,
                    )
                if (result !is EnvelopeDecryptResult.Success) {
                    // On non-success the repository did not insert a pending ack; log
                    // and leave the envelope on the server queue for the next drain.
                    Log.d(TAG, "drain envelope ${envelope.messageId} result=$result")
                }
            }

            messageRepository.flushPendingAcks()
            return envelopes.size
        }

        companion object {
            private const val TAG = "MessageDrainWorker"
            const val WORK_NAME = "message-drain"
            private const val MAX_RETRIES = 3
            private const val DRAIN_TIMEOUT_MS = 30_000L

            /**
             * Enqueue an expedited drain. Uses [ExistingWorkPolicy.KEEP] so that
             * a burst of FCM wakes collapses onto a single in-flight drain, and
             * [OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST] so the worker
             * still runs (just not expedited) if the app is out of expedited
             * quota.
             */
            fun enqueue(context: Context) {
                val constraints =
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                val request =
                    OneTimeWorkRequestBuilder<MessageDrainWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .setConstraints(constraints)
                        .build()

                WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            }
        }
    }
