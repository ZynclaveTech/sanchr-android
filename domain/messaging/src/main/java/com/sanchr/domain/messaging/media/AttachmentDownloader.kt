package com.sanchr.domain.messaging.media

import android.content.Context
import android.util.Log
import android.webkit.MimeTypeMap
import com.sanchr.core.crypto.MediaEncryptor
import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.network.media.BlobStore
import com.sanchr.core.network.media.BlobStoreException
import com.sanchr.proto.media.GetDownloadUrlRequest
import com.sanchr.proto.media.MediaServiceClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AttachmentException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Resolves a message attachment to a decrypted file in the app's private
 * cache, as iOS `MediaDownloadManager`: `sanchr-media://` references go
 * through `GetDownloadUrl`, the blob is fetched and opened under the key
 * carried in the message (single box or iOS chunked layout). Cached by
 * message id so a bubble that scrolls back into view does not re-download.
 */
@Singleton
class AttachmentDownloader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val mediaClient: MediaServiceClient,
        private val blobStore: BlobStore,
    ) {
        private val inFlight = Mutex()

        /** @throws AttachmentException when the media is gone (403/404/410), the blob fails to decrypt, or transport fails. */
        suspend fun open(
            messageId: String,
            attachment: MediaAttachment,
        ): File {
            val target = cacheFile(messageId, attachment.mimeType)
            if (target.isFile && target.length() > 0) return target
            return inFlight.withLock {
                if (target.isFile && target.length() > 0) return@withLock target
                val ciphertext = fetch(attachment)
                val key = attachment.keyBytes()
                val plaintext =
                    if (key == null) {
                        ciphertext
                    } else {
                        try {
                            MediaEncryptor.openAny(ciphertext, key)
                        } catch (e: Exception) {
                            throw AttachmentException("attachment for $messageId failed to decrypt", e)
                        }
                    }
                withContext(Dispatchers.IO) {
                    target.parentFile?.mkdirs()
                    val tmp = File(target.path + ".part")
                    tmp.writeBytes(plaintext)
                    if (!tmp.renameTo(target)) throw AttachmentException("could not write cache for $messageId")
                }
                target
            }
        }

        /** Removes the decrypted copy (and any partial download) for [messageId]; true when something was deleted. */
        suspend fun evict(
            messageId: String,
            mimeType: String,
        ): Boolean =
            inFlight.withLock {
                withContext(Dispatchers.IO) {
                    val target = cacheFile(messageId, mimeType)
                    val part = File(target.path + ".part")
                    val deleted = target.delete()
                    part.delete()
                    deleted
                }
            }

        fun cacheFile(
            messageId: String,
            mimeType: String,
        ): File {
            val ext =
                MimeTypeMap
                    .getSingleton()
                    .getExtensionFromMimeType(mimeType)
                    ?.let { ".$it" }
                    .orEmpty()
            return File(File(context.cacheDir, CACHE_DIR), "$messageId$ext")
        }

        private suspend fun fetch(attachment: MediaAttachment): ByteArray {
            val url =
                attachment.mediaId?.let { mediaId ->
                    try {
                        mediaClient.getDownloadUrl(GetDownloadUrlRequest(mediaId)).url
                    } catch (e: Exception) {
                        throw AttachmentException("no download URL for media $mediaId", e)
                    }
                } ?: attachment.url
            return try {
                blobStore.get(url)
            } catch (e: BlobStoreException) {
                Log.w(TAG, "attachment download failed: HTTP ${e.statusCode}")
                throw AttachmentException(if (e.statusCode in GONE_STATUSES) "media is no longer available" else "download failed", e)
            }
        }

        private companion object {
            const val TAG = "AttachmentDownloader"
            const val CACHE_DIR = "attachments"
            val GONE_STATUSES = setOf(403, 404, 410)
        }
    }
