package com.sanchr.core.mediaviewer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Re-encodes a picked image before it is uploaded.
 *
 * Everything the app sent previously was the file the picker handed over,
 * byte for byte. Three consequences, all of them real:
 *
 *  - **Location.** A camera photo carries EXIF, and EXIF carries GPS. A
 *    profile photo or a snapshot sent to a group published where it was
 *    taken. Decoding and re-encoding keeps the pixels and drops every
 *    metadata block, which is the only reliable way to be rid of it.
 *  - **Size.** A 12 MP HEIC or PNG went out at full size, to be paid for by
 *    the sender's data plan, the recipient's, and the bill for storing it.
 *  - **Format.** HEIC is not decodable everywhere; JPEG is.
 *
 * iOS re-encodes every picked photo to JPEG at 0.92 before sending, so this
 * is also what parity requires.
 *
 * The decode is bounded by [BoundedBitmaps], so a hostile or merely enormous
 * image cannot choose this process's allocation.
 */
object ImageSendEncoder {
    /** iOS sends `jpegData(compressionQuality: 0.92)`. */
    const val QUALITY = 92

    /**
     * Ceiling on the longer side. Above a phone screen's worth of pixels the
     * extra detail is invisible in a chat bubble and costs real data; 4096
     * keeps every current phone camera's full frame.
     */
    const val MAX_LONG_EDGE = 4096

    const val JPEG_MIME = "image/jpeg"

    /** An animated GIF has to stay a GIF: re-encoding it keeps one frame. */
    const val GIF_MIME = "image/gif"

    data class Encoded(
        val bytes: ByteArray,
        val mimeType: String,
        val width: Int,
        val height: Int,
    ) {
        // ByteArray needs these by hand; a data class compares it by identity.
        override fun equals(other: Any?): Boolean =
            this === other ||
                (
                    other is Encoded &&
                        bytes.contentEquals(other.bytes) &&
                        mimeType == other.mimeType &&
                        width == other.width &&
                        height == other.height
                )

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + width
            result = 31 * result + height
            return result
        }
    }

    /** Whether [mimeType] is an image this encoder will rewrite. */
    fun handles(mimeType: String): Boolean = mimeType.startsWith("image/") && mimeType != GIF_MIME

    /**
     * Re-encodes the image at [uri]. Returns null when it cannot be decoded,
     * so the caller can fall back to sending the original bytes rather than
     * failing the send.
     */
    fun encode(
        resolver: ContentResolver,
        uri: Uri,
        maxLongEdge: Int = MAX_LONG_EDGE,
        quality: Int = QUALITY,
    ): Encoded? {
        val decoded = BoundedBitmaps.decode(resolver, uri) ?: return null
        return try {
            encode(decoded, maxLongEdge, quality)
        } finally {
            decoded.recycle()
        }
    }

    private fun encode(
        source: Bitmap,
        maxLongEdge: Int,
        quality: Int,
    ): Encoded? {
        val scaled = scaleToFit(source, maxLongEdge)
        return try {
            val out = ByteArrayOutputStream()
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) return null
            Encoded(out.toByteArray(), JPEG_MIME, scaled.width, scaled.height)
        } finally {
            if (scaled !== source) scaled.recycle()
        }
    }

    private fun scaleToFit(
        source: Bitmap,
        maxLongEdge: Int,
    ): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        if (maxLongEdge <= 0 || longEdge <= maxLongEdge) return source
        val ratio = maxLongEdge.toDouble() / longEdge
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }
}
