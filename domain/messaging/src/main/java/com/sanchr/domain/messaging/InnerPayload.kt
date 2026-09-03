package com.sanchr.domain.messaging

import java.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Encodes a [ByteArray] as a base64 string, matching Swift's default
 * `JSONEncoder` behaviour for `Data` fields. `java.util.Base64`'s default
 * encoder/decoder — padded, no line wrapping — produces the same output.
 */
private object ByteArrayBase64Serializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ByteArrayBase64", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: ByteArray,
    ) {
        encoder.encodeString(Base64.getEncoder().encodeToString(value))
    }

    override fun deserialize(decoder: Decoder): ByteArray = Base64.getDecoder().decode(decoder.decodeString())
}

/**
 * The envelope inside a sealed-sender ciphertext.
 *
 * This is a wire contract with the iOS client, whose `InnerPayload`
 * (SanchrShared/Crypto/SealedSenderManager.swift) is encoded by Swift's
 * default `JSONEncoder` — so byte fields are base64 strings and the keys are
 * snake_case. Do not rename a key or change an encoding without changing iOS
 * in the same release.
 *
 * `v` exists so a reader can tell a payload from the bare UTF-8 text older
 * Android builds sent; see [isInnerPayload].
 */
@Serializable
data class InnerPayload(
    val v: Int = CURRENT_VERSION,
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("content_type") val contentType: String = "text",
    @Serializable(with = ByteArrayBase64Serializer::class) val content: ByteArray = ByteArray(0),
    @SerialName("is_sync") val isSync: Boolean = false,
    @SerialName("expires_after_secs") val expiresAfterSecs: Long? = null,
    @SerialName("sender_profile_key")
    @Serializable(with = ByteArrayBase64Serializer::class)
    val senderProfileKey: ByteArray? = null,
    @SerialName("sender_user_id") val senderUserId: String? = null,
    @SerialName("sender_device_id") val senderDeviceId: Int? = null,
    @SerialName("reply_to_message_id") val replyToMessageId: String? = null,
) {
    fun encode(): ByteArray = JSON.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    // ByteArray compares by reference in a data class, so equals/hashCode are
    // written out by hand to compare content instead.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InnerPayload) return false
        return v == other.v &&
            conversationId == other.conversationId &&
            messageId == other.messageId &&
            contentType == other.contentType &&
            content.contentEquals(other.content) &&
            isSync == other.isSync &&
            expiresAfterSecs == other.expiresAfterSecs &&
            senderProfileKeyContentEquals(other.senderProfileKey) &&
            senderUserId == other.senderUserId &&
            senderDeviceId == other.senderDeviceId &&
            replyToMessageId == other.replyToMessageId
    }

    // A null key and a present-but-empty key are different payloads, so this
    // is written as an explicit three-way case rather than folding "null" and
    // "empty" together via an Elvis chain.
    private fun senderProfileKeyContentEquals(otherKey: ByteArray?): Boolean =
        when {
            senderProfileKey == null && otherKey == null -> true
            senderProfileKey == null || otherKey == null -> false
            else -> senderProfileKey.contentEquals(otherKey)
        }

    override fun hashCode(): Int {
        var result = v
        result = 31 * result + conversationId.hashCode()
        result = 31 * result + (messageId?.hashCode() ?: 0)
        result = 31 * result + contentType.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + isSync.hashCode()
        result = 31 * result + (expiresAfterSecs?.hashCode() ?: 0)
        result = 31 * result + (senderProfileKey?.contentHashCode() ?: 0)
        result = 31 * result + (senderUserId?.hashCode() ?: 0)
        result = 31 * result + (senderDeviceId ?: 0)
        result = 31 * result + (replyToMessageId?.hashCode() ?: 0)
        return result
    }

    companion object {
        const val CURRENT_VERSION = 1

        /** Lenient so a newer iOS build's extra fields do not break an older Android reader. */
        private val JSON =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            }

        fun decode(bytes: ByteArray): InnerPayload? =
            runCatching { JSON.decodeFromString(serializer(), String(bytes, Charsets.UTF_8)) }.getOrNull()

        /**
         * Whether these bytes are a payload rather than the bare UTF-8 text
         * older Android builds sent. Matches iOS's own check: a JSON object
         * carrying a `v` key.
         */
        fun isInnerPayload(bytes: ByteArray): Boolean =
            runCatching {
                val element = JSON.parseToJsonElement(String(bytes, Charsets.UTF_8))
                element is JsonObject && element.jsonObject.containsKey("v")
            }.getOrDefault(false)
    }
}
