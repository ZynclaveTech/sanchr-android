package com.sanchr.core.network.media

import android.util.Log
import com.sanchr.core.network.BuildConfig
import com.sanchr.proto.media.ConfirmUploadRequest
import com.sanchr.proto.media.GetUploadUrlRequest
import com.sanchr.proto.media.MediaPurpose
import com.sanchr.proto.media.MediaServiceClient
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads a profile photo the way iOS does (`ProfileDataSource.uploadAvatar`):
 * SHA-256 the bytes → `GetUploadUrl(purpose = AVATAR)` → PUT to the
 * presigned URL with `x-amz-acl: public-read` → `ConfirmUpload` → the
 * permanent display URL.
 *
 * Avatars are deliberately not encrypted: the CDN URL is what the server's
 * photo-visibility setting gates, and what peers load directly. The URL
 * itself travels to peers encrypted, inside the profile (see
 * `EncryptedProfileUpdater`).
 */
@Singleton
class AvatarUploader
    @Inject
    constructor(
        private val mediaClient: MediaServiceClient,
        private val blobStore: BlobStore,
    ) {
        /** @return the absolute URL the avatar is served from. */
        suspend fun upload(
            bytes: ByteArray,
            contentType: String,
        ): String {
            require(bytes.isNotEmpty()) { "avatar is empty" }
            val presigned =
                mediaClient.getUploadUrl(
                    GetUploadUrlRequest(
                        fileSize = bytes.size.toLong(),
                        contentType = contentType,
                        sha256Hex = sha256Hex(bytes),
                        purpose = MediaPurpose.AVATAR,
                    ),
                )
            blobStore.put(presigned.url, bytes, contentType, headers = mapOf(ACL_HEADER to PUBLIC_READ))
            mediaClient.confirmUpload(ConfirmUploadRequest(mediaId = presigned.mediaId, fileSize = bytes.size.toLong()))
            Log.i(TAG, "avatar uploaded, mediaId=${presigned.mediaId}")
            return resolveDisplayUrl(presigned.displayUrl, presigned.url, BuildConfig.MEDIA_BASE_URL)
        }

        companion object {
            private const val TAG = "AvatarUploader"
            private const val ACL_HEADER = "x-amz-acl"
            private const val PUBLIC_READ = "public-read"

            /**
             * The server's `display_url` may be absolute, a path relative to
             * the media base, or empty (no CDN). Falls back to the upload URL
             * with its presigned query stripped, which is the object's
             * permanent address on a public-read bucket.
             */
            fun resolveDisplayUrl(
                displayUrl: String,
                uploadUrl: String,
                mediaBaseUrl: String,
            ): String {
                if (displayUrl.isNotEmpty()) {
                    if (runCatching { URI(displayUrl).scheme }.getOrNull() != null) return displayUrl
                    val path = if (displayUrl.startsWith("/")) displayUrl else "/$displayUrl"
                    return mediaBaseUrl.trimEnd('/') + path
                }
                return runCatching {
                    val u = URI(uploadUrl)
                    URI(u.scheme, u.authority, u.path, null, null).toString()
                }.getOrDefault(uploadUrl)
            }

            fun sha256Hex(bytes: ByteArray): String =
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }
