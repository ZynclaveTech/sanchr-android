package com.sanchr.domain.messaging.media

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deletes the app's disposable cache.
 *
 * Settings → Storage has a "Clear Cache" button which, before this existed,
 * emitted a success event and deleted nothing: the user tapped it, was told
 * the cache was cleared, and the same number came back on the next refresh.
 *
 * Everything under `cacheDir` is by definition re-derivable — decrypted
 * attachment copies ([AttachmentDownloader]'s `attachments/`), image and link
 * previews, partial downloads. A bubble whose file is deleted here fetches it
 * again on next view, which is exactly what the button promises.
 */
@Singleton
class MediaCacheCleaner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** Bytes currently held in the cache directory. */
        suspend fun cachedBytes(): Long = withContext(Dispatchers.IO) { sizeOf(context.cacheDir) }

        /**
         * Empties the cache directory. Returns the bytes freed, measured
         * before the delete so the caller can say how much went.
         *
         * The directory itself is kept — only its contents go — because the
         * framework hands out that path once and code holding it would
         * otherwise write into a directory that no longer exists.
         */
        suspend fun clear(): Long =
            withContext(Dispatchers.IO) {
                val freed = sizeOf(context.cacheDir)
                context.cacheDir.listFiles()?.forEach { child ->
                    if (!child.deleteRecursively()) {
                        Log.w(TAG, "could not fully delete cache entry ${child.name}")
                    }
                }
                // The external cache is used for files handed to other apps
                // (document previews, shares) and is just as disposable.
                context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                freed
            }

        private fun sizeOf(file: File?): Long {
            if (file == null || !file.exists()) return 0L
            if (file.isFile) return file.length()
            return file.listFiles()?.sumOf { sizeOf(it) } ?: 0L
        }

        private companion object {
            const val TAG = "MediaCacheCleaner"
        }
    }
