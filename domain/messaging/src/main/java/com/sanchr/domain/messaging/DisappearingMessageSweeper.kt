package com.sanchr.domain.messaging

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Makes a disappearing message go when its time is up.
 *
 * Expired messages were removed only by the background sync, whenever the
 * system next chose to run it. Until then a message that had "disappeared"
 * sat in the chat, on screen, for as long as the app stayed open — and was
 * still there the next time it was opened.
 *
 * This waits for the earliest deadline among the messages held, removes what
 * is due, and waits for the next. It watches the database for that deadline,
 * so a message that arrives with a shorter timer than the one being waited
 * on is waited for instead.
 */
class DisappearingMessageSweeper
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val expireMessages: ExpireMessagesUseCase,
    ) {
        /** Runs until cancelled. [now] is the wall clock, in epoch millis. */
        suspend fun run(now: () -> Long = System::currentTimeMillis) {
            messageRepository.observeNextExpiryMillis().collectLatest { next ->
                if (next == null) return@collectLatest
                delay((next - now()).coerceAtLeast(0))
                try {
                    // The deadline itself is due: `expires_at <= now`.
                    expireMessages(maxOf(now(), next))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Left for the next change to the messages, or the
                    // background sync, to try again.
                    Log.w(TAG, "could not remove expired messages", e)
                }
            }
        }

        private companion object {
            const val TAG = "DisappearingSweeper"
        }
    }
