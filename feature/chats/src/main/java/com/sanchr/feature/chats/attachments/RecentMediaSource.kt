package com.sanchr.feature.chats.attachments

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import android.util.Log
import android.util.LruCache
import android.util.Size
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One photo or video from the device's library, newest first in the tray. */
data class RecentMedia(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    /** Zero for a photo, and for a video whose length the library does not know. */
    val durationMs: Long,
)

/**
 * The device's most recent photos and videos, for the attachment tray.
 *
 * Reads the library's index only: no file is opened until a thumbnail is
 * drawn or a photo is picked, and nothing leaves the device from here.
 */
class RecentMediaSource(
    private val context: Context,
) {
    private val thumbnails = LruCache<Long, Bitmap>(THUMBNAIL_CACHE_SIZE)

    fun access(): MediaAccess {
        fun holds(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                if (holds(Manifest.permission.READ_EXTERNAL_STORAGE)) MediaAccess.GRANTED else MediaAccess.DENIED
            holds(Manifest.permission.READ_MEDIA_IMAGES) || holds(Manifest.permission.READ_MEDIA_VIDEO) -> MediaAccess.GRANTED
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                holds(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.LIMITED
            else -> MediaAccess.DENIED
        }
    }

    /** Newest first. Empty when the library cannot be read, which [access] explains. */
    suspend fun recents(limit: Int = RECENTS_LIMIT): List<RecentMedia> =
        withContext(Dispatchers.IO) {
            if (access() == MediaAccess.DENIED) return@withContext emptyList()
            try {
                query(limit)
            } catch (e: SecurityException) {
                // Access taken away between the check and the read.
                Log.w(TAG, "the photo library refused the read", e)
                emptyList()
            }
        }

    private fun query(limit: Int): List<RecentMedia> {
        val found = ArrayList<RecentMedia>(limit)
        context.contentResolver
            .query(
                MediaStore.Files.getContentUri(EXTERNAL_VOLUME),
                arrayOf(FileColumns._ID, FileColumns.MEDIA_TYPE, MediaStore.Video.VideoColumns.DURATION),
                "${FileColumns.MEDIA_TYPE} IN (?, ?)",
                arrayOf(FileColumns.MEDIA_TYPE_IMAGE.toString(), FileColumns.MEDIA_TYPE_VIDEO.toString()),
                "${FileColumns.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idAt = cursor.getColumnIndexOrThrow(FileColumns._ID)
                val typeAt = cursor.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)
                val durationAt = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
                while (found.size < limit && cursor.moveToNext()) {
                    val id = cursor.getLong(idAt)
                    val isVideo = cursor.getInt(typeAt) == FileColumns.MEDIA_TYPE_VIDEO
                    val collection =
                        if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    found +=
                        RecentMedia(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            isVideo = isVideo,
                            durationMs = if (isVideo) cursor.getLong(durationAt) else 0,
                        )
                }
            }
        return found
    }

    /** A thumbnail already drawn once, so scrolling back to it does not flash. */
    fun cachedThumbnail(media: RecentMedia): Bitmap? = thumbnails.get(media.id)

    /** Null when the file is gone or is not something the device can draw. */
    suspend fun thumbnail(
        media: RecentMedia,
        sidePx: Int,
    ): Bitmap? =
        cachedThumbnail(media) ?: withContext(Dispatchers.IO) {
            runCatching { load(media, sidePx) }
                .onFailure { Log.w(TAG, "no thumbnail for a recent item", it) }
                .getOrNull()
                ?.also { thumbnails.put(media.id, it) }
        }

    @Suppress("DEPRECATION") // The only thumbnail API there is below Android 10.
    private fun load(
        media: RecentMedia,
        sidePx: Int,
    ): Bitmap? =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                context.contentResolver.loadThumbnail(media.uri, Size(sidePx, sidePx), null)
            media.isVideo ->
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    media.id,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null,
                )
            else ->
                MediaStore.Images.Thumbnails.getThumbnail(
                    context.contentResolver,
                    media.id,
                    MediaStore.Images.Thumbnails.MINI_KIND,
                    null,
                )
        }

    companion object {
        private const val TAG = "RecentMediaSource"
        private const val RECENTS_LIMIT = 60

        // MediaStore.VOLUME_EXTERNAL, which is only a named constant from Android 10.
        private const val EXTERNAL_VOLUME = "external"
        private const val THUMBNAIL_CACHE_SIZE = 80

        /** What to ask for, which differs by Android version. */
        fun permissions(): Array<String> =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                    )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
    }
}
