package com.sanchr.domain.messaging.media

import com.sanchr.core.crypto.MediaEncryptor
import com.sanchr.core.network.media.BlobStore
import com.sanchr.proto.media.ConfirmUploadRequest
import com.sanchr.proto.media.ConfirmUploadResponse
import com.sanchr.proto.media.GetDownloadUrlRequest
import com.sanchr.proto.media.GetUploadUrlRequest
import com.sanchr.proto.media.MediaPurpose
import com.sanchr.proto.media.MediaServiceClient
import com.sanchr.proto.media.PresignedUrlResponse
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AttachmentUploaderTest {
    private class Client : MediaServiceClient {
        var upload: GetUploadUrlRequest? = null
        var confirmed: ConfirmUploadRequest? = null

        override suspend fun getUploadUrl(request: GetUploadUrlRequest): PresignedUrlResponse {
            upload = request
            return PresignedUrlResponse("https://bucket/put", "media-7", 900, "")
        }

        override suspend fun getDownloadUrl(request: GetDownloadUrlRequest): PresignedUrlResponse = error("not used")

        override suspend fun confirmUpload(request: ConfirmUploadRequest): ConfirmUploadResponse {
            confirmed = request
            return ConfirmUploadResponse(request.mediaId)
        }
    }

    private class Store : BlobStore {
        var put: ByteArray? = null

        override suspend fun put(
            url: String,
            bytes: ByteArray,
            contentType: String,
            headers: Map<String, String>,
            onProgress: ((Long, Long) -> Unit)?,
        ) {
            put = bytes
            onProgress?.invoke(bytes.size.toLong() / 2, bytes.size.toLong())
            onProgress?.invoke(bytes.size.toLong(), bytes.size.toLong())
        }

        override suspend fun get(url: String): ByteArray = error("not used")
    }

    @Test
    fun `uploads ciphertext only and returns an iOS-shaped attachment whose key opens it`() =
        runTest {
            val client = Client()
            val store = Store()
            val plaintext = "photo bytes".toByteArray()

            val attachment =
                AttachmentUploader(client, store).upload(
                    AttachmentUploader.Prepared(plaintext, "image/jpeg", "a.jpg", caption = "c", width = 10, height = 20),
                )

            val uploaded = requireNotNull(store.put)
            assertFalse(uploaded.contentEquals(plaintext))
            assertEquals(MediaPurpose.ATTACHMENT, client.upload!!.purpose)
            assertEquals(uploaded.size.toLong(), client.upload!!.fileSize)
            assertEquals(ConfirmUploadRequest("media-7", uploaded.size.toLong()), client.confirmed)

            assertEquals("sanchr-media://media-7", attachment.url)
            assertEquals("media-7", attachment.mediaId)
            assertEquals("image/jpeg", attachment.mimeType)
            assertEquals(plaintext.size.toLong(), attachment.sizeBytes)
            assertEquals("a.jpg", attachment.filename)
            assertEquals(10, attachment.width)
            val key = requireNotNull(attachment.keyBytes())
            assertEquals(32, key.size)
            assertContentEquals(uploaded.copyOfRange(0, 12), Base64.getDecoder().decode(attachment.encryptionIV))
            assertTrue(plaintext.contentEquals(MediaEncryptor.openAny(uploaded, key)))
        }

    @Test
    fun `reports upload progress as a fraction that ends at one`() =
        runTest {
            val seen = mutableListOf<Float>()

            AttachmentUploader(Client(), Store()).upload(
                AttachmentUploader.Prepared("photo bytes".toByteArray(), "image/jpeg", "a.jpg"),
            ) { seen += it }

            assertEquals(2, seen.size)
            assertTrue(seen.first() > 0f && seen.first() < 1f)
            assertEquals(1f, seen.last())
        }

    @Test
    fun `a voice note carries the iOS voice fields`() =
        runTest {
            val attachment =
                AttachmentUploader(Client(), Store()).upload(
                    AttachmentUploader.Prepared(
                        byteArrayOf(1, 2, 3),
                        "audio/mp4",
                        "voice-1.m4a",
                        isVoiceMessage = true,
                        audioDurationMs = 4200,
                        audioWaveform = listOf(0.1f, 0.9f),
                    ),
                )
            assertEquals(true, attachment.isVoiceMessage)
            assertEquals(4200, attachment.audioDurationMs)
            assertEquals(listOf(0.1f, 0.9f), attachment.audioWaveform)
            assertEquals("voice-1.m4a", attachment.filename)
        }

    @Test
    fun `an image's blurHash rides on the attachment for the peer's placeholder`() =
        runTest {
            val attachment =
                AttachmentUploader(Client(), Store()).upload(
                    AttachmentUploader.Prepared(
                        byteArrayOf(1, 2, 3),
                        "image/jpeg",
                        "p.jpg",
                        width = 4,
                        height = 3,
                        blurHash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj",
                    ),
                )
            assertEquals("LEHV6nWB2yk8pyo0adR*.7kCMdnj", attachment.blurHash)
        }

    @Test
    fun `a view-once photo is stamped isViewOnce for the peer's client`() =
        runTest {
            val prepared = AttachmentUploader.Prepared(byteArrayOf(1), "image/jpeg", "p.jpg", width = 4, height = 3).asViewOnce()
            val attachment = AttachmentUploader(Client(), Store()).upload(prepared)
            assertEquals(true, attachment.isViewOnce)
            assertEquals(4, attachment.width)
        }
}
