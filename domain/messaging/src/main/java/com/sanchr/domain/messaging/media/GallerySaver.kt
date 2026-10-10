package com.sanchr.domain.messaging.media

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Copies a decrypted attachment into the device's shared gallery.
 *
 * Uses MediaStore rather than a raw file write so it works without storage
 * permission on Android 10 and up, which is every device this app supports bar
 * the oldest. Returns whether the copy actually landed.
 *
 * Lives in the domain layer because two callers need it: the explicit Save
 * button in the media viewer, and Media Auto-Save, which copies incoming media
 * as it is downloaded ([MediaAutoSavePolicy] decides which).
 */
object GallerySaver {
    fun save(
        context: Context,
        file: File,
        mimeType: String,
    ): Boolean =
        runCatching {
            val isVideo = MediaAutoSavePolicy.isVideo(mimeType)
            val collection =
                if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            val directory = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "sanchr-${System.currentTimeMillis()}")
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/Sanchr")
                    }
                }
            val uri = context.contentResolver.insert(collection, values) ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: return@runCatching false
            true
        }.getOrDefault(false)
}
