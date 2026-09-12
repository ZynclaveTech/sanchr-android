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
            val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
            val file = File(directory, "capture-${System.currentTimeMillis()}.jpg")
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
}
