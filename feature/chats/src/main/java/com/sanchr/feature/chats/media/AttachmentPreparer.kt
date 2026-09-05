package com.sanchr.feature.chats.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
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
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return null
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name =
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment
        var width: Int? = null
        var height: Int? = null
        var blurHash: String? = null
        if (mime.startsWith("image/")) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth > 0) width = bounds.outWidth
            if (bounds.outHeight > 0) height = bounds.outHeight
            blurHash = BlurHashImages.encode(bytes)
        }
        return AttachmentUploader.Prepared(bytes, mime, name, width = width, height = height, blurHash = blurHash)
    }
}
