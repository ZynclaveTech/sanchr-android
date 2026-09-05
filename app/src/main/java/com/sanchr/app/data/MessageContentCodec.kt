package com.sanchr.app.data

import com.sanchr.core.model.ContactCard
import com.sanchr.core.model.MediaContentEnvelope
import com.sanchr.core.model.MediaKind
import com.sanchr.core.model.MessageContent
import org.json.JSONObject

/**
 * Turns a stored (`content_type`, `content_body`) pair into [MessageContent].
 * Attachment bodies are the iOS `MessageContent` envelope
 * ([MediaContentEnvelope]); anything that does not parse falls back to text
 * rather than being dropped, so a peer's malformed payload is visible, not
 * silently lost.
 */
object MessageContentCodec {
    fun fromStored(
        contentType: String,
        body: String,
    ): MessageContent =
        when (contentType) {
            "text" -> MessageContent.Text(body)
            "location" -> location(body)
            ContactCard.CONTENT_TYPE -> contact(body)
            else -> MediaKind.fromWire(contentType)?.let { media(body, it) } ?: MessageContent.Text(body)
        }

    private fun media(
        body: String,
        kind: MediaKind,
    ): MessageContent? {
        val decoded = MediaContentEnvelope.decode(body, kind) ?: return null
        val a = decoded.attachments.first()
        return when (decoded.kind) {
            MediaKind.IMAGE, MediaKind.VIDEO ->
                MessageContent.Image(
                    url = a.url,
                    thumbnailUrl = a.thumbnailUrl,
                    width = a.width ?: 0,
                    height = a.height ?: 0,
                    caption = a.caption,
                    attachment = a,
                )
            MediaKind.AUDIO ->
                MessageContent.Voice(
                    url = a.url,
                    durationMs = a.audioDurationMs?.toLong() ?: a.durationSeconds?.let { (it * MILLIS_PER_SECOND).toLong() } ?: 0L,
                    waveform = a.audioWaveform.orEmpty(),
                    attachment = a,
                )
            MediaKind.DOCUMENT ->
                MessageContent.File(
                    url = a.url,
                    fileName = a.filename ?: a.url.substringAfterLast('/'),
                    mimeType = a.mimeType,
                    sizeBytes = a.sizeBytes,
                    attachment = a,
                )
        }
    }

    private fun contact(body: String): MessageContent =
        ContactCard.decode(body)?.let { MessageContent.Contact(it.name, it.phoneNumber) } ?: MessageContent.Text("[Contact]")

    private fun location(body: String): MessageContent =
        runCatching {
            val json = JSONObject(body)
            MessageContent.Location(
                latitude = json.getDouble("latitude"),
                longitude = json.getDouble("longitude"),
                label = json.optString("label").takeIf { it.isNotBlank() },
            )
        }.getOrElse { MessageContent.Text("[Location]") }

    private const val MILLIS_PER_SECOND = 1000.0
}
