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
 * Sends several photos or videos as one message, the way iOS sends an album.
 *
 * Android sent a batch as one message per file, so picking ten photos
 * produced ten bubbles on both sides and ten notifications for the recipient,
 * where iOS produced one collage. The wire envelope has always carried a list
 * of attachments — the receive path on both platforms reads one — so this is
 * the send side of the same shape.
 *
 * Uploads happen in order and the message is sent only once every attachment
 * is up: a partial album is not a smaller album, it is a wrong one.
 */
class SendAlbumUseCase
    @Inject
    constructor(
        private val uploader: AttachmentUploader,
        private val sendMessageUseCase: SendMessageUseCase,
        private val dispatcherProvider: DispatcherProvider,
    ) {
        /**
         * @param prepared two or more files. A single file is sent as an
         *   ordinary attachment by [SendAttachmentUseCase]; an album of one is
         *   not a thing either platform draws.
         * @param onProgress fraction of the whole set written, 0..1.
         */
        suspend operator fun invoke(
            conversationId: String,
            prepared: List<AttachmentUploader.Prepared>,
            replyToId: String? = null,
            onProgress: ((Float) -> Unit)? = null,
        ): Result<Message> =
            withContext(dispatcherProvider.io) {
                runCatchingResult {
                    require(prepared.size > 1) { "an album needs more than one attachment" }
                    val uploaded =
                        prepared.mapIndexed { index, file ->
                            uploader.upload(file) { fraction ->
                                onProgress?.invoke((index + fraction) / prepared.size)
                            }
                        }
                    // The kind of the set, taken from the first: a mixed pick
                    // of photos and video travels as `image`, which is what
                    // iOS does with the same mixture, and the per-attachment
                    // mime type still decides how each tile renders.
                    val kind = MediaKind.forMimeType(uploaded.first().mimeType)
                    val body = MediaContentEnvelope.encode(kind, uploaded)
                    when (val sent = sendMessageUseCase(conversationId, body, contentType = kind.wire, replyToId = replyToId)) {
                        is Result.Success -> sent.data
                        is Result.Error -> throw sent.exception
                        is Result.Loading -> error("unexpected loading state from send")
                    }
                }
            }
    }
