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
         * One message's files.
         *
         * A failure is logged and passed over: a file that would not delete
         * must not be what keeps the message itself on the phone.
         *
         * @return what was removed, so the caller can remove it [again] once
         *         the message's row is gone.
         */
        suspend fun evict(messageId: String): List<CachedFile> =
            try {
                val files =
                    messageRepository
                        .message(messageId)
                        ?.let(AutoVaultRules::cachedAttachments)
                        .orEmpty()
                        .map { (cacheId, attachment) -> CachedFile(cacheId, attachment.mimeType) }
                again(files)
                files
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "could not find cached media for $messageId", e)
                emptyList()
            }

        /**
         * Removes [files] once more, after their messages' rows have gone.
         *
         * Between the first removal and the row going, a chat that is open
         * can ask for the picture again and have it downloaded and decrypted
         * afresh. Nothing asks for it once the row is gone, so this pass is
         * the one that sticks. The first is still needed: it is the only one
         * if the app dies in between, when the rows that say which files
         * these are would already be gone.
         */
        suspend fun again(files: List<CachedFile>) {
            for (file in files) {
                try {
                    attachmentDownloader.evict(file.cacheId, file.mimeType)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "could not remove cached media ${file.cacheId}", e)
                }
            }
        }

        /** A decrypted file in the cache: the id it is stored under and its type. */
        data class CachedFile(
            val cacheId: String,
            val mimeType: String,
        )

        private companion object {
            const val TAG = "MessageMediaEvictor"
        }
    }
