package com.sanchr.feature.chats.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.sanchr.core.mediaviewer.ImageSendEncoder
import com.sanchr.domain.messaging.media.AttachmentUploader

/**
 * Reads a `content://` file into the shape the uploader wants.
 *
 * Shared by the in-chat picker and the system share sheet, so a photo sent
 * from another app carries the same dimensions and BlurHash placeholder as
 * one attached from inside a conversation.
 *
 * Call from a background dispatcher: it reads the whole file.
 */
object AttachmentPreparer {
    /**
     * Largest file that may be sent, matching the server's own limit
     * (`MAX_UPLOAD_SIZE`, 100 MiB) and iOS's `FileSource.maxBytes`.
     *
     * Checked here, before the bytes are read, because reading comes first and
     * costs about three times the file in heap — the file, its ciphertext and
     * the copy handed to the upload. Android had no limit at all, so a long 4K
     * clip was an unavoidable `OutOfMemoryError` on the way to an upload the
     * server would have refused anyway.
     */
    const val MAX_BYTES: Long = 100L * 1024 * 1024

    /** Whether a file of [bytes] may be sent. */
    fun isSizeAcceptable(bytes: Long): Boolean = bytes in 1..MAX_BYTES

    /** The size the provider reports for [uri], or null when it will not say. */
    fun reportedSize(
        context: Context,
        uri: Uri,
    ): Long? =
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
        }.getOrNull()

    /**
     * The file at [uri], or null when it cannot be read or is too large to
     * send.
     *
     * @param readFrom where the bytes actually come from, when a step before
     *   this one produced a replacement — a re-encoded video. The name, type
     *   and reported size still come from [uri], which is what the user picked.
     */
    fun prepare(
        context: Context,
        uri: Uri,
        readFrom: Uri = uri,
    ): AttachmentUploader.Prepared? {
        val resolver = context.contentResolver
        val reported = reportedSize(context, uri)
        if (reported != null && reported > MAX_BYTES) return null
        val sourceMime = resolver.getType(uri) ?: "application/octet-stream"
        // Photos are re-encoded rather than forwarded as picked. The original
        // carries EXIF — including GPS — and whatever size and format the
        // camera chose; a JPEG made here carries pixels and nothing else, as
        // iOS has always sent them.
        val reencoded =
            if (ImageSendEncoder.handles(sourceMime)) {
                runCatching { ImageSendEncoder.encode(resolver, readFrom) }.getOrNull()
            } else {
                null
            }
        val bytes =
            reencoded?.bytes
                ?: runCatching { resolver.openInputStream(readFrom)?.use { it.readBytes() } }.getOrNull()
                ?: return null
        val mime = reencoded?.mimeType ?: sourceMime
        val name =
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()?.let { if (reencoded != null) it.substringBeforeLast('.') + ".jpg" else it }
                ?: uri.lastPathSegment
        var width: Int? = null
        var height: Int? = null
        var blurHash: String? = null
        if (mime.startsWith("image/")) {
            if (reencoded != null) {
                width = reencoded.width
                height = reencoded.height
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth > 0) width = bounds.outWidth
                if (bounds.outHeight > 0) height = bounds.outHeight
            }
            blurHash = BlurHashImages.encode(bytes)
        }
        return AttachmentUploader.Prepared(bytes, mime, name, width = width, height = height, blurHash = blurHash)
    }
}
