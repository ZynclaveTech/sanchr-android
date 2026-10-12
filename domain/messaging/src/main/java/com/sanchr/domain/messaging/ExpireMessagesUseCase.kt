package com.sanchr.domain.messaging

import javax.inject.Inject

/**
 * Removes every disappearing message whose time is up, and what it had put
 * on this phone's disk.
 *
 * Expiry used to delete the rows and nothing else, so a disappearing photo
 * that had been opened stayed, decrypted, in the cache after the message that
 * carried it was gone. The files go first, while the rows still say which
 * they are — and once more after, for the reason [MessageMediaEvictor.again]
 * gives.
 *
 * @return how many messages were removed.
 */
class ExpireMessagesUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val mediaEvictor: MessageMediaEvictor,
    ) {
        suspend operator fun invoke(nowMillis: Long): Int {
            val expired = messageRepository.expiredMessageIds(nowMillis)
            if (expired.isEmpty()) return 0
            val files = expired.flatMap { mediaEvictor.evict(it) }
            val removed = messageRepository.deleteExpiredMessages(nowMillis)
            mediaEvictor.again(files)
            return removed
        }
    }
