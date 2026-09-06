package com.sanchr.feature.chats.media.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Where an edited photo lands so the send path can pick it up again.
 *
 * It has to come back as a `content://` URI, not a `file://` one:
 * `AttachmentPreparer` asks the resolver for the MIME type, and a file URI
 * answers null — the photo would be uploaded as `application/octet-stream`
 * and arrive as a nameless download rather than an image.
 *
 * Written to the cache directory, so the system can reclaim it and an edit
 * that is never sent does not accumulate.
 */
object EditedImageStore {
    private const val DIRECTORY = "edited"
    private const val QUALITY = 92

    // Two edits of the same photo can land in one millisecond, and a
    // timestamp alone would then have the second overwrite the first — the
    // strip would show two entries pointing at one file.
    private val sequence =
        java.util.concurrent.atomic
            .AtomicLong(0)

    /**
     * [bitmap] written as a JPEG, as a URI the uploader can read.
     *
     * JPEG at 92 rather than PNG: these are photographs, and a lossless
     * encode of a large one is several times the size for no visible gain.
     * Returns null if the write fails, so the caller keeps the original
     * rather than sending nothing.
     */
    fun save(
        context: Context,
        bitmap: Bitmap,
    ): Uri? =
        runCatching {
            val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
            val file = File(directory, "edited-${System.currentTimeMillis()}-${sequence.incrementAndGet()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
}
