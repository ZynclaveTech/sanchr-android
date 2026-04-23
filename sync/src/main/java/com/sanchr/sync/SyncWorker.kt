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
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanchr.core.crypto.SignalKeyManager
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.notifications.NotificationHandler
import com.sanchr.domain.messaging.EnvelopeDecryptResult
import com.sanchr.domain.messaging.EnvelopeKind
import com.sanchr.domain.messaging.IncomingEnvelopeContext
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.ReceiveMessageUseCase
import com.sanchr.domain.messaging.ServerProvidedSender
import com.sanchr.proto.auth.AuthServiceClient
import com.sanchr.proto.auth.RefreshTokenRequest
import com.sanchr.proto.messaging.GetConversationsRequest
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.SyncRequest
import com.sanchr.sync.backup.ChatBackupManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.grpc.StatusException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import org.json.JSONArray

/**
 * Background worker that periodically syncs messages, conversations, keys, and
 * local cleanup with the server. Runs on a 15-minute interval when the device
 * has network connectivity.
 *
 * Phases executed in order:
 * 1. Refresh auth token if expiring soon
 * 2. Sync pending/missed messages via SyncMessages RPC
 * 3. Refresh conversations from server
 * 4. Replenish Signal pre-keys if below threshold
 * 5. Clean expired vault items and messages locally
 * 6. Update notification badge count
 */
@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val messagingClient: MessagingServiceClient,
        private val authClient: AuthServiceClient,
        private val keyManager: SignalKeyManager,
        private val receiveMessageUseCase: ReceiveMessageUseCase,
        private val sessionManager: SessionManager,
        private val notificationHandler: NotificationHandler,
        private val conversationDao: ConversationDao,
        private val messageDao: MessageDao,
        private val messageRepository: MessageRepository,
        private val chatBackupManager: ChatBackupManager,
        private val syncState: SyncState,
    ) : CoroutineWorker(appContext, params) {
        companion object {
            private const val TAG = "SyncWorker"
            const val WORK_NAME = "sanchr_sync"
            const val ONE_TIME_WORK = "sanchr_sync_once"
            private const val MAX_RETRIES = 3

            /** Token refresh buffer: refresh if expiring within 5 minutes. */
            private const val TOKEN_REFRESH_BUFFER_MS = 5 * 60 * 1000L

            /**
             * Schedule periodic sync every 15 minutes, requiring network connectivity.
             * Uses [ExistingPeriodicWorkPolicy.KEEP] so that re-scheduling does not
             * reset the existing timer.
             */
            fun schedulePeriodic(workManager: WorkManager) {
                val constraints =
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                val request =
                    PeriodicWorkRequestBuilder<SyncWorker>(
                        repeatInterval = 15,
                        repeatIntervalTimeUnit = TimeUnit.MINUTES,
                    ).setConstraints(constraints)
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            30,
                            TimeUnit.SECONDS,
                        ).build()

                workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
                Log.i(TAG, "Periodic sync scheduled (every 15 min)")
            }

            /**
             * Trigger an immediate one-time sync. Uses [ExistingWorkPolicy.REPLACE]
             * so that rapid successive calls do not queue redundant work.
             */
            fun syncNow(workManager: WorkManager) {
                val constraints =
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                val request =
                    OneTimeWorkRequestBuilder<SyncWorker>()
                        .setConstraints(constraints)
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            15,
                            TimeUnit.SECONDS,
                        ).build()

                workManager.enqueueUniqueWork(
                    ONE_TIME_WORK,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
                Log.i(TAG, "One-time sync enqueued")
            }

            /**
             * Cancel all sync work -- both periodic and one-time.
             */
            fun cancelAll(workManager: WorkManager) {
                workManager.cancelUniqueWork(WORK_NAME)
                workManager.cancelUniqueWork(ONE_TIME_WORK)
                Log.i(TAG, "All sync work cancelled")
            }
        }

        override suspend fun doWork(): Result {
            // Do not sync if user is not authenticated
            if (sessionManager.getAccessToken() == null) {
                Log.w(TAG, "Skipping sync: no access token")
                return Result.success()
            }

            syncState.markSyncStarted()

            return try {
                // Phase 1: Refresh auth token if expiring
                val tokenValid = refreshTokenIfNeeded()
                if (!tokenValid) {
                    syncState.markSyncFailed("Authentication expired")
                    return Result.failure()
                }

                // Phase 2 & 3 can run concurrently
                val newMessageCount: Int
                coroutineScope {
                    val messagesDeferred = async { syncPendingMessages() }
                    val conversationsDeferred = async { refreshConversations() }

                    newMessageCount = messagesDeferred.await()
                    conversationsDeferred.await()
                }

                // Phase 4 & 5 can run concurrently
                coroutineScope {
                    val preKeysDeferred = async { replenishPreKeys() }
                    val cleanupDeferred = async { cleanExpiredVaultItems() }

                    preKeysDeferred.await()
                    cleanupDeferred.await()
                }

                chatBackupManager.performScheduledBackupIfNeeded()

                // Phase 6: Update notification badge
                updateBadge(newMessageCount)

                syncState.markSyncCompleted(newMessageCount)
                Log.i(TAG, "Sync completed successfully (newMessages=$newMessageCount)")
                Result.success()
            } catch (e: StatusException) {
                val code = e.status.code
                Log.e(TAG, "Sync failed with gRPC status: $code", e)

                // UNAUTHENTICATED or PERMISSION_DENIED are not retryable
                if (code == io.grpc.Status.Code.UNAUTHENTICATED ||
                    code == io.grpc.Status.Code.PERMISSION_DENIED
                ) {
                    syncState.markSyncFailed("Authentication error: ${code.name}")
                    return Result.failure()
                }

                retryOrFail(e.message ?: "gRPC error")
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                retryOrFail(e.message ?: "Unknown error")
            }
        }

        /**
         * Returns [Result.retry] if under the retry limit, otherwise [Result.failure].
         */
        private fun retryOrFail(errorMessage: String): Result =
            if (runAttemptCount < MAX_RETRIES) {
                syncState.markSyncFailed("$errorMessage (retrying)")
                Result.retry()
            } else {
                syncState.markSyncFailed(errorMessage)
                Result.failure()
            }

        // ------------------------------------------------------------------
        // Phase 1: Token refresh
        // ------------------------------------------------------------------

        /**
         * Refreshes the access token if it is expired or about to expire.
         *
         * @return `true` if a valid token is available after this call, `false` if
         *         the refresh failed (e.g., refresh token revoked).
         */
        private suspend fun refreshTokenIfNeeded(): Boolean {
            val expiry = sessionManager.getTokenExpiry()
            val now = System.currentTimeMillis()

            // Token is still valid and not close to expiry
            if (expiry > 0 && (expiry - now) > TOKEN_REFRESH_BUFFER_MS) {
                return true
            }

            val refreshToken = sessionManager.getRefreshToken() ?: return false
            val deviceId = sessionManager.getDeviceId() ?: ""

            return try {
                val response =
                    authClient.refreshToken(
                        RefreshTokenRequest(
                            refreshToken = refreshToken,
                            deviceId = deviceId,
                        ),
                    )

                val newExpiry = now + (response.expiresIn * 1000L)
                sessionManager.updateAccessToken(response.accessToken, newExpiry)
                Log.d(TAG, "Token refreshed, expires in ${response.expiresIn}s")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh failed", e)
                false
            }
        }

        // ------------------------------------------------------------------
        // Phase 2: Sync messages
        // ------------------------------------------------------------------

        /**
         * Fetches messages from the server that arrived since the last sync
         * timestamp. Stores them locally and returns the count of new messages.
         */
        private suspend fun syncPendingMessages(): Int {
            val lastSync = syncState.lastSyncTimestamp.value ?: 0L

            val envelopes =
                messagingClient
                    .syncMessages(
                        SyncRequest(
                            sinceTimestamp = lastSync,
                        ),
                    ).toList()

            if (envelopes.isEmpty()) {
                messageRepository.flushPendingAcks()
                return 0
            }

            var persistedCount = 0
            for (envelope in envelopes) {
                val senderDeviceId = envelope.senderDevice.takeIf { it > 0 } ?: 1
                val result =
                    receiveMessageUseCase.receive(
                        envelopeBytes = envelope.cipherText,
                        kind = EnvelopeKind.NON_SEALED,
                        serverTimestamp = envelope.serverTimestamp,
                        declaredSender = ServerProvidedSender(envelope.senderId, senderDeviceId),
                        envelopeContext =
                            IncomingEnvelopeContext(
                                conversationId = envelope.conversationId,
                                messageId = envelope.messageId,
                                contentType = envelope.contentType,
                            ),
                        // Batch acks — one RPC after the drain loop, not N per envelope.
                        flushAckImmediately = false,
                    )
                if (result is EnvelopeDecryptResult.Success) {
                    persistedCount += 1
                } else {
                    Log.d(TAG, "Sync envelope ${envelope.messageId} handled with result=$result")
                }
            }

            messageRepository.flushPendingAcks()
            Log.d(TAG, "Synced and persisted $persistedCount message envelope(s)")
            return persistedCount
        }

        // ------------------------------------------------------------------
        // Phase 3: Refresh conversations
        // ------------------------------------------------------------------

        /**
         * Fetches the latest conversation list from the server and upserts them
         * into the local database.
         */
        private suspend fun refreshConversations() {
            val response =
                messagingClient.getConversations(
                    GetConversationsRequest(),
                )

            val entities =
                response.conversations.map { conv ->
                    ConversationEntity(
                        id = conv.id,
                        type = conv.type.uppercase(),
                        title = conv.title.ifEmpty { null },
                        avatarUrl = conv.avatarUrl.ifEmpty { null },
                        participantIds = JSONArray(conv.participantIds.toTypedArray()).toString(),
                        lastMessagePreview = conv.lastMessagePreview.ifEmpty { null },
                        lastMessageTimestamp = conv.lastMessageTimestamp.takeIf { it > 0 },
                        unreadCount = conv.unreadCount,
                        isPinned = conv.isPinned,
                        isMuted = conv.isMuted,
                        updatedAt = conv.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        createdAt = conv.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    )
                }

            if (entities.isNotEmpty()) {
                conversationDao.insertConversations(entities)
                Log.d(TAG, "Refreshed ${entities.size} conversation(s)")
            }
        }

        // ------------------------------------------------------------------
        // Phase 4: Replenish pre-keys
        // ------------------------------------------------------------------

        /**
         * Checks the server-side pre-key count and uploads a new batch if the
         * count has fallen below the configured threshold.
         */
        private suspend fun replenishPreKeys() {
            try {
                val userId = sessionManager.getUserId()
                val deviceId = sessionManager.getDeviceId()?.toIntOrNull()

                if (userId != null && deviceId != null && !keyManager.hasCompleteServerBundle(userId, deviceId)) {
                    keyManager.uploadInitialKeyBundle()
                } else {
                    keyManager.checkAndReplenishPreKeys()
                    keyManager.rotateSignedPreKeyIfNeeded()
                }
            } catch (e: Exception) {
                // Pre-key replenishment is best-effort; do not fail the entire sync
                Log.w(TAG, "Pre-key replenishment failed (non-fatal)", e)
            }
        }

        // ------------------------------------------------------------------
        // Phase 5: Clean expired items
        // ------------------------------------------------------------------

        /**
         * Removes locally-cached messages and vault items whose expiry timestamp
         * has passed.
         */
        private suspend fun cleanExpiredVaultItems() {
            val now = System.currentTimeMillis()
            val deletedCount = messageDao.deleteExpiredMessages(now)
            if (deletedCount > 0) {
                Log.d(TAG, "Cleaned $deletedCount expired message(s)")
            }
        }

        // ------------------------------------------------------------------
        // Phase 6: Badge update
        // ------------------------------------------------------------------

        /**
         * Updates the notification summary badge with the count of newly synced
         * messages.
         */
        private suspend fun updateBadge(newMessageCount: Int) {
            if (newMessageCount > 0) {
                notificationHandler.updateSummaryNotification(newMessageCount)
            }
        }
    }
