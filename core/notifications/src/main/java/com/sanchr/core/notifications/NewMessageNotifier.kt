package com.sanchr.core.notifications

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.datastore.SessionManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Observes the local messages table and renders a notification for each
 * incoming (non-self) message that lands after the notifier starts.
 *
 * This is the only code path that produces user-visible "new message"
 * notifications in Phase C — the FCM transport no longer carries content
 * ([PushPayload] is wake-only), and the push service no longer calls
 * [NotificationHandler] directly. Once [MessageDrainWorker] inserts a
 * decrypted row via [com.sanchr.domain.messaging.ReceiveMessageUseCase],
 * this observer fires.
 *
 * De-dupe: messages are keyed by id, and the notifier remembers the last
 * rendered id set so a Flow re-emission (e.g. a status update) does not
 * re-post the same notification.
 */
@Singleton
class NewMessageNotifier
    @Inject
    constructor(
        private val messageDao: MessageDao,
        private val contactDao: ContactDao,
        private val sessionManager: SessionManager,
        private val notificationHandler: NotificationHandler,
        private val dispatchers: DispatcherProvider,
    ) {
        private var startJob: Job? = null
        private val renderedIds = mutableSetOf<String>()

        /**
         * Starts the observer on [scope]. Idempotent — a second call is a
         * no-op while the first observer is still running. Emits
         * notifications only for messages with `timestamp > now`, so a
         * restart does not re-notify for pre-start history.
         */
        fun start(scope: CoroutineScope) {
            if (startJob?.isActive == true) return
            val selfUserId = sessionManager.getUserId()
            if (selfUserId.isNullOrBlank()) {
                Log.d(TAG, "notifier not started: no user id")
                return
            }
            val startTime = System.currentTimeMillis()
            startJob =
                scope.launch(dispatchers.io) {
                    runCatching {
                        messageDao
                            .observeIncomingMessagesAfter(startTime, selfUserId)
                            .collectLatest { batch ->
                                batch.forEach { entity ->
                                    if (renderedIds.add(entity.id)) {
                                        val senderName = resolveSenderName(entity.senderId)
                                        notificationHandler.showMessageNotification(
                                            entity = entity,
                                            senderDisplayName = senderName,
                                        )
                                    }
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
        }
    }
