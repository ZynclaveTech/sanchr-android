package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.model.MediaAttachment
import com.sanchr.domain.messaging.media.AttachmentDownloader
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * What happens when the viewer of view-once media closes it, as iOS's
 * gallery does on dismiss: the decrypted bytes are wiped from the cache
 * first, then the row becomes a "Viewed" tombstone and the server is asked
 * to drop the message. Order matters: the bytes must be gone before the
 * bubble flips, so a crash in between leaves nothing viewable behind.
 */
class ConsumeViewOnceUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val attachmentDownloader: AttachmentDownloader,
        private val dispatcherProvider: DispatcherProvider,
    ) {
        suspend operator fun invoke(
            messageId: String,
            attachment: MediaAttachment,
        ) {
            withContext(dispatcherProvider.io) {
                attachmentDownloader.evict(messageId, attachment.mimeType)
                messageRepository.tombstoneViewOnce(messageId)
            }
        }
    }
