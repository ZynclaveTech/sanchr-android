package com.sanchr.core.network.media

import com.sanchr.proto.media.ConfirmUploadRequest
import com.sanchr.proto.media.ConfirmUploadResponse
import com.sanchr.proto.media.GetDownloadUrlRequest
import com.sanchr.proto.media.GetUploadUrlRequest
import com.sanchr.proto.media.MediaPurpose
import com.sanchr.proto.media.MediaServiceClient
import com.sanchr.proto.media.PresignedUrlResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class AvatarUploaderTest {
    private class RecordingMediaClient(
        private val presigned: PresignedUrlResponse,
    ) : MediaServiceClient {
        var uploadRequest: GetUploadUrlRequest? = null
        var confirmRequest: ConfirmUploadRequest? = null

        override suspend fun getUploadUrl(request: GetUploadUrlRequest): PresignedUrlResponse {
            uploadRequest = request
            return presigned
        }

        override suspend fun getDownloadUrl(request: GetDownloadUrlRequest): PresignedUrlResponse = error("not used")

        override suspend fun confirmUpload(request: ConfirmUploadRequest): ConfirmUploadResponse {
            confirmRequest = request
            return ConfirmUploadResponse(request.mediaId)
        }
    }

    private class RecordingBlobStore(
        private val failWith: Throwable? = null,
    ) : BlobStore {
        var url: String? = null
        var bytes: ByteArray? = null
        var contentType: String? = null
        var headers: Map<String, String> = emptyMap()

        override suspend fun get(url: String): ByteArray = error("not used")

        override suspend fun put(
            url: String,
            bytes: ByteArray,
            contentType: String,
            headers: Map<String, String>,
            onProgress: ((Long, Long) -> Unit)?,
        ) {
            failWith?.let { throw it }
            this.url = url
            this.bytes = bytes
            this.contentType = contentType
            this.headers = headers
        }
    }

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)
    private val presigned =
        PresignedUrlResponse(
            url = "https://bucket.example/avatars/m-1.jpg?X-Amz-Signature=abc&X-Amz-Expires=900",
            mediaId = "m-1",
            expiresInSecs = 900,
            displayUrl = "https://cdn.example/avatars/m-1.jpg",
        )

    @Test
    fun `hashes, asks for an avatar upload, PUTs public-read, confirms, returns the display url`() =
        runTest {
            val client = RecordingMediaClient(presigned)
            val store = RecordingBlobStore()

            val url = AvatarUploader(client, store).upload(jpeg, "image/jpeg")

            val req = requireNotNull(client.uploadRequest)
            assertEquals(jpeg.size.toLong(), req.fileSize)
            assertEquals("image/jpeg", req.contentType)
            assertEquals(AvatarUploader.sha256Hex(jpeg), req.sha256Hex)
            assertEquals(MediaPurpose.AVATAR, req.purpose)

            assertEquals(presigned.url, store.url)
            assertEquals("image/jpeg", store.contentType)
            assertEquals("public-read", store.headers["x-amz-acl"])

            assertEquals(ConfirmUploadRequest("m-1", jpeg.size.toLong()), client.confirmRequest)
            assertEquals("https://cdn.example/avatars/m-1.jpg", url)
        }

    @Test
    fun `a failed PUT propagates and the upload is never confirmed`() =
        runTest {
            val client = RecordingMediaClient(presigned)
            val store = RecordingBlobStore(failWith = BlobStoreException(403, "PUT failed: HTTP 403"))

            assertFailsWith<BlobStoreException> { AvatarUploader(client, store).upload(jpeg, "image/jpeg") }

            assertNull(client.confirmRequest)
        }

    @Test
    fun `empty bytes are refused before any RPC`() =
        runTest {
            val client = RecordingMediaClient(presigned)
            assertFailsWith<IllegalArgumentException> { AvatarUploader(client, RecordingBlobStore()).upload(ByteArray(0), "image/jpeg") }
            assertNull(client.uploadRequest)
        }

    @Test
    fun `display url resolution matches iOS`() {
        val base = "https://sanchr-media.example"
        val upload = "https://bucket.example/avatars/m-1.jpg?X-Amz-Signature=abc"
        // Absolute display URL wins as-is.
        assertEquals("https://cdn/x.jpg", AvatarUploader.resolveDisplayUrl("https://cdn/x.jpg", upload, base))
        // Relative path resolves against the media base, with or without a leading slash.
        assertEquals("$base/avatars/m-1.jpg", AvatarUploader.resolveDisplayUrl("/avatars/m-1.jpg", upload, "$base/"))
        assertEquals("$base/avatars/m-1.jpg", AvatarUploader.resolveDisplayUrl("avatars/m-1.jpg", upload, base))
        // No CDN: the upload URL minus its presigned query.
        assertEquals("https://bucket.example/avatars/m-1.jpg", AvatarUploader.resolveDisplayUrl("", upload, base))
    }

    @Test
    fun `sha256 is lowercase hex of the bytes`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", AvatarUploader.sha256Hex(ByteArray(0)))
    }
}
