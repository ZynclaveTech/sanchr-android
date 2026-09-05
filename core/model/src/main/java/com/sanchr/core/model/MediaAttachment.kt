package com.sanchr.core.model

import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * One attachment as it travels inside an end-to-end encrypted message —
 * iOS `Message.MediaAttachment`, Swift `Codable` defaults (camelCase keys,
 * `Data` as base64, `URL` as string). The bytes live in object storage as
 * AES-GCM ciphertext under [encryptionKey]; only someone who can read the
 * message can read the file.
 */
@Serializable
data class MediaAttachment(
    /** `sanchr-media://<mediaId>` for uploaded media; a plain URL for legacy links. */
    val url: String,
    /** Base64 of the 32-byte AES key. Empty for unencrypted legacy links. */
    val encryptionKey: String = "",
    /** Base64 of the 12-byte GCM nonce (also the first 12 bytes of the blob). */
    val encryptionIV: String = "",
    val mimeType: String,
    /** Plaintext size. */
    val sizeBytes: Long = 0,
    @SerialName("thumbnailURL") val thumbnailUrl: String? = null,
    val caption: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Double? = null,
    val blurHash: String? = null,
    val filename: String? = null,
    val isVoiceMessage: Boolean? = null,
    val audioDurationMs: Int? = null,
    val audioWaveform: List<Float>? = null,
    val isViewOnce: Boolean? = null,
) {
    /** The media object id when [url] is a `sanchr-media://` reference, else null. */
    val mediaId: String?
        get() =
            url
                .takeIf { it.startsWith(MEDIA_SCHEME) }
                ?.removePrefix(MEDIA_SCHEME)
                ?.trimEnd('/')
                ?.takeIf { it.isNotEmpty() }

    fun keyBytes(): ByteArray? =
        encryptionKey.takeIf { it.isNotEmpty() }?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

    companion object {
        const val MEDIA_SCHEME = "sanchr-media://"

        fun mediaUrl(mediaId: String): String = MEDIA_SCHEME + mediaId
    }
}

/** Which `Message.MessageContent` case an attachment message is; also the wire `content_type`. */
enum class MediaKind(
    val wire: String,
) {
    IMAGE("image"),
    VIDEO("video"),
    AUDIO("audio"),
    DOCUMENT("document"),
    ;

    companion object {
        fun forMimeType(mime: String): MediaKind =
            when {
                mime.startsWith("image/") -> IMAGE
                mime.startsWith("video/") -> VIDEO
                mime.startsWith("audio/") -> AUDIO
                else -> DOCUMENT
            }

        fun fromWire(contentType: String): MediaKind? =
            when (contentType) {
                "image" -> IMAGE
                "video" -> VIDEO
                "audio", "voice" -> AUDIO
                "document", "file" -> DOCUMENT
                else -> null
            }
    }
}

/**
 * The plaintext of an attachment message: Swift's encoding of the
 * `Message.MessageContent` enum, `{"image":{"_0":[<attachment>…]}}`.
 * Decoding also accepts the legacy single-object form `{"image":{"_0":{…}}}`
 * and, for rows this app wrote before it spoke the iOS format, a bare
 * attachment object.
 */
object MediaContentEnvelope {
    private val JSON =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = false
        }

    class Decoded(
        val kind: MediaKind,
        val attachments: List<MediaAttachment>,
    )

    fun encode(
        kind: MediaKind,
        attachments: List<MediaAttachment>,
    ): String {
        require(attachments.isNotEmpty()) { "an attachment message needs at least one attachment" }
        val list = JSON.encodeToJsonElement(ListSerializer(MediaAttachment.serializer()), attachments)
        return JSON.encodeToString(JsonObject(mapOf(kind.wire to JsonObject(mapOf("_0" to list)))))
    }

    /** @param fallbackKind the message's `content_type`, used when the body is a bare attachment. */
    fun decode(
        body: String,
        fallbackKind: MediaKind?,
    ): Decoded? {
        val root = runCatching { JSON.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        MediaKind.entries
            .firstNotNullOfOrNull { kind ->
                (root[kind.wire] as? JsonObject)?.get("_0")?.let { payload -> decodeAttachments(payload)?.let { Decoded(kind, it) } }
            }?.let { return it }
        val bare = decodeAttachments(root) ?: return null
        val kind = fallbackKind ?: MediaKind.forMimeType(bare.first().mimeType)
        return Decoded(kind, bare)
    }

    private fun decodeAttachments(element: JsonElement): List<MediaAttachment>? =
        runCatching { JSON.decodeFromJsonElement(ListSerializer(MediaAttachment.serializer()), element) }
            .recoverCatching { listOf(JSON.decodeFromJsonElement(MediaAttachment.serializer(), element)) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
}
