package com.sanchr.domain.messaging

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Files the received messages that could not be filed when they arrived.
 *
 * Decrypting an envelope moves the session on, so it cannot be decrypted
 * twice. A message that decrypted and then failed to save used to be dropped
 * on the spot — its content gone, and no redelivery able to bring it back.
 * It is now held ([MessageRepository.holdIncoming]) and tried again here: at
 * launch, and after each sync, which is when whatever was missing — the
 * conversation, usually — is most likely to have turned up.
 */
class HeldIncomingRetrier
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val receiveMessage: ReceiveMessageUseCase,
    ) {
        /** @return how many were filed. */
        suspend fun retry(): Int {
            var filed = 0
            for (held in messageRepository.heldIncoming()) {
                try {
                    receiveMessage.file(held.decrypted, held.envelope)
                    messageRepository.releaseIncoming(held.id)
                    filed++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "a held message still cannot be filed (attempt ${held.attempts + 1})", e)
                    giveUpOrKeep(held)
                }
            }
            return filed
        }

        /**
         * Something that has failed this many times, across this many
         * launches and syncs, is not waiting on anything that will arrive.
         * Let go, so it is not carried and retried for ever.
         */
        private suspend fun giveUpOrKeep(held: HeldIncoming) {
            if (held.attempts + 1 >= MAX_ATTEMPTS) {
                Log.e(TAG, "giving up on a held message after $MAX_ATTEMPTS attempts")
                messageRepository.releaseIncoming(held.id)
            } else {
                messageRepository.noteHeldAttempt(held.id)
            }
        }

        companion object {
            const val MAX_ATTEMPTS = 50
            private const val TAG = "HeldIncomingRetrier"
        }
    }
