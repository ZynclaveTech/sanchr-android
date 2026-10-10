package com.sanchr.feature.chats.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Where a photo taken from the chats list is written.
 *
 * `TakePicture` hands the camera a URI to write into and reports only whether
 * it succeeded, so the destination has to exist before the camera opens and
 * be remembered across it.
 *
 * The system camera is used rather than an in-app CameraX screen: it is the
 * camera the user already knows, it handles the permission, and it keeps a
 * viewfinder out of a codebase that does not otherwise have one.
 */
object CapturedPhotos {
    private const val DIRECTORY = "captures"

    /** A fresh `content://` destination, or null when it cannot be created. */
    fun newUri(context: Context): Uri? =
        runCatching {
            val directory = directory(context)
            val file = File(directory, "capture-${System.currentTimeMillis()}.jpg")
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()

    /**
     * Deletes the file behind [uri].
     *
     * The capture is the full-resolution photo in the clear, EXIF and all, and
     * it was left in the cache for good: taking a photo in a chat put a
     * plaintext copy on disk whether it was sent, cancelled or failed. What is
     * sent is a re-encoded copy, so the original has no further use the moment
     * preparation is over.
     */
    fun discard(
        context: Context,
        uri: Uri?,
    ) {
        val name = uri?.lastPathSegment?.substringAfterLast('/') ?: return
        runCatching { File(directory(context), name).delete() }
    }

    /**
     * Removes every capture left behind — by a crash, or by a process death
     * between the camera returning and the send completing.
     *
     * Called at launch: `cacheDir` is disposable, but "eventually, under
     * storage pressure" is not when a photo someone decided not to send
     * should stop existing.
     */
    fun sweep(context: Context) {
        runCatching { directory(context).listFiles()?.forEach { it.delete() } }
    }

    private fun directory(context: Context): File = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
}
