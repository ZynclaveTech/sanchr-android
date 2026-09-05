package com.sanchr.core.model

import kotlinx.datetime.Instant

/**
 * A vault item this device can read: its metadata envelope decrypted under
 * the per-item access key. Items whose key this device does not hold are
 * "sealed" and never surface here.
 */
data class VaultItem(
    val id: String,
    /** The media object holding the encrypted payload; downloaded via MediaService. */
    val mediaId: String,
    val type: VaultItemType,
    val name: String,
    val mimeType: String,
    /** Plaintext size, before encryption. */
    val sizeBytes: Long,
    /** A small JPEG preview generated on this device at upload time, or null. */
    val thumbnailJpeg: ByteArray? = null,
    val createdAt: Instant,
    /** Null when the item does not expire. */
    val expiresAt: Instant? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultItem) return false
        return id == other.id &&
            mediaId == other.mediaId &&
            type == other.type &&
            name == other.name &&
            mimeType == other.mimeType &&
            sizeBytes == other.sizeBytes &&
            (thumbnailJpeg?.contentEquals(other.thumbnailJpeg) ?: (other.thumbnailJpeg == null)) &&
            createdAt == other.createdAt &&
            expiresAt == other.expiresAt
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class VaultItemType {
    PHOTO,
    VIDEO,
    AUDIO,
    DOCUMENT,
    NOTE,
    ;

    companion object {
        /** Same mapping as iOS `VaultUseCases.vaultItemType(fromMimeType:)`. */
        fun fromMimeType(mime: String): VaultItemType =
            when {
                mime.startsWith("image/") -> PHOTO
                mime.startsWith("video/") -> VIDEO
                mime.startsWith("audio/") -> AUDIO
                mime == "text/plain" -> NOTE
                else -> DOCUMENT
            }
    }
}
