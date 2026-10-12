package com.sanchr.domain.messaging

import android.util.Log
import com.sanchr.domain.messaging.media.AttachmentCacheNames
import com.sanchr.domain.messaging.media.AttachmentDownloader
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Removes decrypted photos and videos whose message is no longer on this
 * phone.
 *
 * Deleting a message, clearing or deleting a chat and a disappearing message
 * expiring all remove the cached media now. They did not always: older
 * versions removed the message and left the picture. Nothing in the database
 * points at those files any more, so nothing that works from the database
 * will ever find them. This works from the files instead.
 *
 * It only removes what it is sure of. The cached copy can be the only one
 * left — the server does not keep media for ever — so a file is left alone
 * unless its name gives a message id, no message with media has that id, and
 * it is old enough not to belong to something still being sent or saved.
 */
class OrphanedMediaSweeper
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val attachmentDownloader: AttachmentDownloader,
    ) {
        /** @return how many files were removed. */
        suspend fun sweep(nowMillis: Long = System.currentTimeMillis()): Int =
            try {
                val candidates =
                    attachmentDownloader
                        .cachedFiles()
                        .filter { nowMillis - it.lastModified() >= MIN_AGE_MILLIS }
                        .mapNotNull { file -> AttachmentCacheNames.messageIdOf(file.name)?.let { it to file } }
                if (candidates.isEmpty()) {
                    0
                } else {
                    val holdingMedia = messageRepository.messageIdsHoldingMedia(candidates.map { it.first }.distinct())
                    candidates.count { (id, file) -> id !in holdingMedia && attachmentDownloader.remove(file) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "could not sweep the attachment cache", e)
                0
            }

        companion object {
            /**
             * A message's id changes when the server accepts it, and its file
             * is written before its row in places. An hour is far longer than
             * either takes.
             */
            const val MIN_AGE_MILLIS = 60L * 60 * 1000
            private const val TAG = "OrphanedMediaSweeper"
        }
    }
