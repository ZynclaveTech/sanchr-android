package com.sanchr.domain.messaging

import android.util.Log
import com.sanchr.domain.messaging.media.AttachmentDownloader
import com.sanchr.domain.messaging.vault.AutoVaultRules
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Removes what a message has put on this phone's disk.
 *
 * A photo that has been opened sits decrypted in the cache under its
 * message's id. Anything that deletes a message has to take that with it, and
 * has to do so first — while the row still says which files are its.
 */
class MessageMediaEvictor
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val attachmentDownloader: AttachmentDownloader,
    ) {
        /**
         * One message's files. A failure is logged and passed over: a file
         * that would not delete must not be what keeps the message itself on
         * the phone.
         */
        suspend fun evict(messageId: String) {
            try {
                val message = messageRepository.message(messageId) ?: return
                for ((cacheId, attachment) in AutoVaultRules.cachedAttachments(message)) {
                    attachmentDownloader.evict(cacheId, attachment.mimeType)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "could not remove cached media for $messageId", e)
            }
        }

        private companion object {
            const val TAG = "MessageMediaEvictor"
        }
    }
