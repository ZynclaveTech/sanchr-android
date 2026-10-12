package com.sanchr.domain.messaging.media

import android.content.Context
import android.util.Log
import android.webkit.MimeTypeMap
import com.sanchr.core.crypto.MediaEncryptor
import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.network.link.GuardedLinkDownloader
import com.sanchr.core.network.link.LinkDetector
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
        private val linkDownloader: GuardedLinkDownloader,
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

        /** Every file in the attachment cache, finished or partial. Empty if there is no cache yet. */
        suspend fun cachedFiles(): List<File> =
            withContext(Dispatchers.IO) {
                File(context.cacheDir, CACHE_DIR).listFiles()?.filter { it.isFile }.orEmpty()
            }

        /** Removes one file found by [cachedFiles]; true when it was deleted. */
        suspend fun remove(file: File): Boolean = inFlight.withLock { withContext(Dispatchers.IO) { file.delete() } }

        /**
         * Whether this attachment is already on disk, so it can be shown
         * without spending data. Lets the auto-download policy hold back only
         * the fetches that would actually cost something.
         */
        fun isCached(
            messageId: String,
            attachment: MediaAttachment,
        ): Boolean = cacheFile(messageId, attachment.mimeType).let { it.isFile && it.length() > 0 }

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
            // The id is the sender's string; see [AttachmentCacheNames].
            return File(File(context.cacheDir, CACHE_DIR), AttachmentCacheNames.fileName(messageId) + ext)
        }

        private suspend fun fetch(attachment: MediaAttachment): ByteArray =
            try {
                val mediaId = attachment.mediaId
                if (mediaId != null) blobStore.get(signedUrl(mediaId)) else fetchLink(attachment.url)
            } catch (e: BlobStoreException) {
                Log.w(TAG, "attachment download failed: HTTP ${e.statusCode}")
                throw AttachmentException(if (e.statusCode in GONE_STATUSES) "media is no longer available" else "download failed", e)
            }

        private suspend fun signedUrl(mediaId: String): String =
            try {
                mediaClient.getDownloadUrl(GetDownloadUrlRequest(mediaId)).url
            } catch (e: Exception) {
                throw AttachmentException("no download URL for media $mediaId", e)
            }

        /**
         * Not uploaded media but a URL the sender wrote, and this can run on
         * auto-download with nobody tapping anything. The link has to read as
         * a public `https` address, and it is fetched by a client that checks
         * the same thing of every address it actually connects to.
         */
        private suspend fun fetchLink(url: String): ByteArray {
            if (!isFetchableLink(url)) throw AttachmentException("attachment link is not one this device will fetch")
            return linkDownloader.get(url, MAX_LINK_BYTES)
        }

        companion object {
            /**
             * Whether a plain-URL attachment may be requested: an `https`
             * address of a site on the internet, by the same reading of the
             * host that link previews use. Not the device, not its network,
             * and not over a connection anyone on the path can rewrite.
             */
            fun isFetchableLink(url: String): Boolean {
                if (!url.startsWith("https://", ignoreCase = true)) return false
                val host = LinkDetector.hostOf(url) ?: return false
                return host.contains('.') && !LinkDetector.isPrivateHost(host)
            }

            private const val TAG = "AttachmentDownloader"
            private const val CACHE_DIR = "attachments"

            /** The most a sender's link may deliver: the same ceiling as an attachment that is sent. */
            private const val MAX_LINK_BYTES = 100L * 1024 * 1024
            private val GONE_STATUSES = setOf(403, 404, 410)
        }
    }
