package com.sanchr.domain.messaging

import android.util.Log
import com.sanchr.domain.messaging.media.AttachmentDownloader
import com.sanchr.domain.messaging.vault.AutoVaultRules
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

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
        private val attachmentDownloader: AttachmentDownloader,
    ) {
        suspend operator fun invoke(conversationId: String): Int {
            val ids = messageRepository.messageIds(conversationId)
            for (id in ids) removeCachedMedia(id)
            messageRepository.clearMessages(conversationId)
            return ids.size
        }

        /**
         * One message's files. A failure here is logged and passed over: a
         * file that would not delete must not be what keeps the messages
         * themselves on the phone.
         */
        private suspend fun removeCachedMedia(messageId: String) {
            try {
                val message = messageRepository.message(messageId) ?: return
                for ((cacheId, attachment) in AutoVaultRules.cachedAttachments(message)) {
                    attachmentDownloader.evict(cacheId, attachment.mimeType)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "could not remove cached media for $messageId while clearing its chat", e)
            }
        }

        private companion object {
            const val TAG = "ClearChatUseCase"
        }
    }
