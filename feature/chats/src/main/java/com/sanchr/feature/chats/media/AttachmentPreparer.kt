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
    /** The file at [uri], or null when it cannot be read. */
    fun prepare(
        context: Context,
        uri: Uri,
    ): AttachmentUploader.Prepared? {
        val resolver = context.contentResolver
        val sourceMime = resolver.getType(uri) ?: "application/octet-stream"
        // Photos are re-encoded rather than forwarded as picked. The original
        // carries EXIF — including GPS — and whatever size and format the
        // camera chose; a JPEG made here carries pixels and nothing else, as
        // iOS has always sent them.
        val reencoded =
            if (ImageSendEncoder.handles(sourceMime)) {
                runCatching { ImageSendEncoder.encode(resolver, uri) }.getOrNull()
            } else {
                null
            }
        val bytes =
            reencoded?.bytes
                ?: runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
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
