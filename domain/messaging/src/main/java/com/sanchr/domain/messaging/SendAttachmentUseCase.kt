package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.core.common.runCatchingResult
import com.sanchr.core.model.MediaContentEnvelope
import com.sanchr.core.model.MediaKind
import com.sanchr.core.model.Message
import com.sanchr.domain.messaging.media.AttachmentUploader
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Sends a file as an end-to-end encrypted attachment message: uploads the
 * ciphertext ([AttachmentUploader]), then sends the iOS-shaped
 * `MessageContent` envelope — key and all — through the ordinary sealed
 * message path with `content_type` set to the media kind.
 */
class SendAttachmentUseCase
    @Inject
    constructor(
        private val uploader: AttachmentUploader,
        private val sendMessageUseCase: SendMessageUseCase,
        private val dispatcherProvider: DispatcherProvider,
    ) {
        suspend operator fun invoke(
            conversationId: String,
            prepared: AttachmentUploader.Prepared,
            /** The message this attachment answers, or null; travels in the sealed payload as for text. */
            replyToId: String? = null,
            /** Fraction of the upload written, 0..1, for the composer's progress. */
            onProgress: ((Float) -> Unit)? = null,
        ): Result<Message> =
            withContext(dispatcherProvider.io) {
                runCatchingResult {
                    val attachment = uploader.upload(prepared, onProgress)
                    val kind = MediaKind.forMimeType(attachment.mimeType)
                    val body = MediaContentEnvelope.encode(kind, listOf(attachment))
                    when (val sent = sendMessageUseCase(conversationId, body, contentType = kind.wire, replyToId = replyToId)) {
                        is Result.Success -> sent.data
                        is Result.Error -> throw sent.exception
                        is Result.Loading -> error("unexpected loading state from send")
                    }
                }
            }
    }
