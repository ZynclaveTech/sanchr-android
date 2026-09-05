package com.sanchr.domain.messaging

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.core.model.ContactCard
import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.domain.messaging.media.AttachmentDownloader
import com.sanchr.domain.messaging.media.AttachmentUploader
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Forwards one message into several conversations at once, as iOS
 * `forwardMessage`: text, contact and location bodies are re-sent as-is;
 * media is decrypted once (the same path that draws the bubble) and then
 * uploaded once per destination, because each send seals its own key.
 * Destinations are sent concurrently so three chats do not receive three
 * visibly staggered copies.
 */
class ForwardMessageUseCase
    @Inject
    constructor(
        private val sendMessageUseCase: SendMessageUseCase,
        private val sendAttachmentUseCase: SendAttachmentUseCase,
        private val attachmentDownloader: AttachmentDownloader,
        private val dispatcherProvider: DispatcherProvider,
    ) {
        class Outcome(
            val sent: Int,
            val failed: Int,
        )

        /** @throws UnsupportedContentException for content that cannot be forwarded (a tombstone, legacy media without a key). */
        suspend operator fun invoke(
            message: Message,
            targetConversationIds: List<String>,
        ): Outcome =
            withContext(dispatcherProvider.io) {
                val targets = targetConversationIds.distinct().filter { it.isNotBlank() }
                if (targets.isEmpty()) return@withContext Outcome(0, 0)
                val send: suspend (String) -> Result<Message> = plan(message)
                val results =
                    coroutineScope {
                        targets.map { target -> async { runSend(target, send) } }.awaitAll()
                    }
                Outcome(sent = results.count { it }, failed = results.count { !it })
            }

        private suspend fun runSend(
            target: String,
            send: suspend (String) -> Result<Message>,
        ): Boolean =
            try {
                send(target) is Result.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "forward to $target failed: ${e.message}")
                false
            }

        private suspend fun plan(message: Message): suspend (String) -> Result<Message> =
            when (val content = message.content) {
                is MessageContent.Text -> { target -> sendMessageUseCase(target, content.body) }
                is MessageContent.Contact -> { target ->
                    sendMessageUseCase(target, ContactCard(content.name, content.phoneNumber).encode(), ContactCard.CONTENT_TYPE)
                }
                is MessageContent.Location -> { target -> sendMessageUseCase(target, content.toWire(), "location") }
                is MessageContent.Image -> media(message.id, content.attachment, content.caption)
                is MessageContent.Voice -> media(message.id, content.attachment, null)
                is MessageContent.File -> media(message.id, content.attachment, null)
                is MessageContent.System -> throw UnsupportedContentException("nothing to forward")
            }

        private suspend fun media(
            messageId: String,
            attachment: MediaAttachment?,
            caption: String?,
        ): suspend (String) -> Result<Message> {
            if (attachment == null) throw UnsupportedContentException("legacy media cannot be forwarded")
            if (attachment.isViewOnce == true) throw UnsupportedContentException("view-once media cannot be forwarded")
            // Decrypted once for the whole fan-out; uploaded per destination below.
            val file = attachmentDownloader.open(messageId, attachment)
            val prepared =
                AttachmentUploader.Prepared(
                    bytes = file.readBytes(),
                    mimeType = attachment.mimeType,
                    fileName = attachment.filename,
                    caption = caption,
                    width = attachment.width,
                    height = attachment.height,
                    durationSeconds = attachment.durationSeconds,
                    isVoiceMessage = attachment.isVoiceMessage,
                    audioDurationMs = attachment.audioDurationMs,
                    audioWaveform = attachment.audioWaveform,
                    blurHash = attachment.blurHash,
                )
            return { target -> sendAttachmentUseCase(target, prepared) }
        }

        private fun MessageContent.Location.toWire(): String =
            JSONObject()
                .put("latitude", latitude)
                .put("longitude", longitude)
                .apply { label?.let { put("label", it) } }
                .toString()

        private companion object {
            const val TAG = "ForwardMessageUseCase"
        }
    }

class UnsupportedContentException(
    message: String,
) : Exception(message)
