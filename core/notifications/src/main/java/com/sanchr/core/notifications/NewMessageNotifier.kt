package com.sanchr.core.notifications

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.di.ApplicationScope
import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.datastore.NotifierState
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Observes the local messages table and renders a notification for each
 * incoming (non-self) message that lands after the last-notified high-water
 * mark.
 *
 * This is the only code path that produces user-visible "new message"
 * notifications in Phase C — the FCM transport no longer carries content
 * ([PushPayload] is wake-only), and the push service no longer calls
 * [NotificationHandler] directly. Once [MessageDrainWorker] inserts a
 * decrypted row via [com.sanchr.domain.messaging.ReceiveMessageUseCase],
 * this observer fires.
 *
 * **De-dupe** is bounded and restart-safe:
 *  - [renderedIds] is a bounded LRU so a pathologically long-running
 *    notifier does not accumulate an unbounded Set.
 *  - The last-rendered timestamp is persisted via [NotifierState] so a
 *    cold start resumes at the correct high-water mark and does not
 *    re-notify for messages the user already saw.
 *
 * **Display-name resolution** is cached per batch — contacts rarely
 * change within a notification burst, so the blocking DAO call fires at
 * most once per unique sender per batch instead of once per envelope.
 */
@Singleton
class NewMessageNotifier
    @Inject
    constructor(
        private val messageDao: MessageDao,
        private val conversationDao: ConversationDao,
        private val contactDao: ContactDao,
        private val sessionManager: SessionManager,
        private val userPreferences: UserPreferences,
        private val notifierState: NotifierState,
        private val notificationHandler: NotificationHandler,
        private val dispatchers: DispatcherProvider,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        private var startJob: Job? = null

        /**
         * Bounded LRU of message ids that have already produced a
         * notification. Bounded to [RENDERED_ID_CAPACITY] so a long-running
         * notifier cannot leak memory on a chatty account. Room's Flow can
         * re-emit the same batch when unrelated columns update (e.g. read
         * receipts), and we need to suppress those duplicates.
         *
         * [LinkedHashMap] with `accessOrder = true` makes eviction LRU.
         */
        private val renderedIds: MutableMap<String, Boolean> =
            object : LinkedHashMap<String, Boolean>(RENDERED_ID_CAPACITY, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>): Boolean = size > RENDERED_ID_CAPACITY
            }

        /**
         * Starts the observer on the app-wide scope. Idempotent — a second
         * call is a no-op while the first observer is still running.
         *
         * The observer resumes from the persisted high-water mark
         * ([NotifierState.getLastNotifiedTimestamp]), falling back to
         * "now" when there is no prior mark (fresh install).
         */
        fun start() {
            if (startJob?.isActive == true) return
            val selfUserId = sessionManager.getUserId()
            if (selfUserId.isNullOrBlank()) {
                Log.d(TAG, "notifier not started: no user id")
                return
            }
            startJob =
                applicationScope.launch(dispatchers.io) {
                    runCatching {
                        val resumeFrom =
                            notifierState.getLastNotifiedTimestamp() ?: System.currentTimeMillis()
                        messageDao
                            .observeIncomingMessagesAfter(resumeFrom, selfUserId)
                            .collectLatest { batch ->
                                if (batch.isEmpty()) return@collectLatest
                                // Read the preference once per batch — cheap and
                                // avoids re-renders when the toggle flips mid-flow.
                                val showPreview =
                                    runCatching { userPreferences.showPreviewOnLockscreen.first() }
                                        .getOrDefault(false)
                                // A read that fails falls back to the strictest
                                // option: never show more than the user asked for.
                                val previewSetting =
                                    runCatching { userPreferences.notificationPreview.first() }
                                        .getOrDefault(NotificationPreviewPolicy.NEVER)
                                val nameCache = mutableMapOf<String, String?>()
                                val mutedCache = mutableMapOf<String, Boolean>()
                                var maxTimestamp = 0L
                                batch.forEach { entity ->
                                    // A muted chat's messages are still marked as seen by the
                                    // notifier so they never surface later when it is unmuted.
                                    val muted =
                                        mutedCache.getOrPut(entity.conversationId) {
                                            runCatching {
                                                conversationDao
                                                    .getConversationById(
                                                        entity.conversationId,
                                                    )?.isMuted == true
                                            }.getOrDefault(false)
                                        }
                                    if (renderedIds.put(entity.id, true) == null && !muted) {
                                        val senderName =
                                            nameCache.getOrPut(entity.senderId) {
                                                resolveSenderName(entity.senderId)
                                            }
                                        notificationHandler.showMessageNotification(
                                            entity = entity,
                                            senderDisplayName = senderName,
                                            showPreviewOnLockscreen = showPreview,
                                            messagePreviewSetting = previewSetting,
                                        )
                                    }
                                    if (entity.timestamp > maxTimestamp) {
                                        maxTimestamp = entity.timestamp
                                    }
                                }
                                if (maxTimestamp > 0L) {
                                    notifierState.advanceLastNotifiedTimestamp(maxTimestamp)
                                }
                            }
                    }.onFailure { error ->
                        Log.w(TAG, "message observer failed", error)
                    }
                }
        }

        /** Stops the observer. Safe to call even if not started. */
        fun stop() {
            startJob?.cancel()
            startJob = null
            renderedIds.clear()
        }

        private suspend fun resolveSenderName(senderId: String): String? =
            runCatching { contactDao.getContactById(senderId)?.displayName }.getOrNull()

        private companion object {
            const val TAG = "NewMessageNotifier"

            /**
             * Upper bound on the in-memory de-dupe set. ~256 entries is
             * roughly two days of mid-volume chat traffic before the LRU
             * starts evicting — well past the window in which Room could
             * re-emit a stale row for the same message.
             */
            const val RENDERED_ID_CAPACITY = 256
        }
    }
