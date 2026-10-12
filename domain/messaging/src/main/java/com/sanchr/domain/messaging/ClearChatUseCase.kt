package com.sanchr.domain.messaging

import javax.inject.Inject

/**
 * Clears a chat: every message in it goes from this phone, and so does
 * everything those messages had put on its disk.
 *
 * The second half is the part that is easy to leave out. A photo that has
 * been opened sits decrypted in the cache under its message's id. Delete the
 * rows and leave the files, and "permanently delete all messages" has
 * removed the list of what was said and kept the pictures. So the files go
 * first — while the rows still say which they are — and the rows after.
 *
 * Local only. The other person's copies are theirs, and nothing is asked of
 * the server.
 *
 * @return how many messages were cleared.
 */
class ClearChatUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val mediaEvictor: MessageMediaEvictor,
    ) {
        suspend operator fun invoke(conversationId: String): Int {
            val ids = messageRepository.messageIds(conversationId)
            for (id in ids) mediaEvictor.evict(id)
            messageRepository.clearMessages(conversationId)
            return ids.size
        }
    }
