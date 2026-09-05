package com.sanchr.app.data

import com.sanchr.app.data.vault.VaultItemMetadata
import com.sanchr.core.crypto.AccessKeyStore
import com.sanchr.core.crypto.DeviceSecretProvider
import com.sanchr.core.crypto.MediaEncryptor
import com.sanchr.core.model.VaultItemType
import com.sanchr.core.network.media.BlobStore
import com.sanchr.proto.media.ConfirmUploadRequest
import com.sanchr.proto.media.ConfirmUploadResponse
import com.sanchr.proto.media.GetDownloadUrlRequest
import com.sanchr.proto.media.GetUploadUrlRequest
import com.sanchr.proto.media.MediaPurpose
import com.sanchr.proto.media.MediaServiceClient
import com.sanchr.proto.media.PresignedUrlResponse
import com.sanchr.proto.vault.CreateVaultItemRequest
import com.sanchr.proto.vault.DeleteVaultItemRequest
import com.sanchr.proto.vault.DeleteVaultItemResponse
import com.sanchr.proto.vault.GetVaultItemRequest
import com.sanchr.proto.vault.GetVaultItemsRequest
import com.sanchr.proto.vault.GetVaultItemsResponse
import com.sanchr.proto.vault.VaultItem
import com.sanchr.proto.vault.VaultServiceClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultRepositoryImplTest {
    private class RecordingVaultClient : VaultServiceClient {
        var created: CreateVaultItemRequest? = null
        var deleted: String? = null
        var page = GetVaultItemsResponse(emptyList(), "")

        override suspend fun createVaultItem(request: CreateVaultItemRequest): VaultItem {
            created = request
            return VaultItem(request.vaultItemId, request.mediaId, request.encryptedMetadata, createdAt = 1_000L, expiresAt = 0L)
        }

        override suspend fun getVaultItems(request: GetVaultItemsRequest) = page

        override suspend fun getVaultItem(request: GetVaultItemRequest): VaultItem = error("not used")

        override suspend fun deleteVaultItem(request: DeleteVaultItemRequest): DeleteVaultItemResponse {
            deleted = request.vaultItemId
            return DeleteVaultItemResponse
        }
    }

    private class RecordingMediaClient : MediaServiceClient {
        var uploadRequest: GetUploadUrlRequest? = null
        var confirmed: ConfirmUploadRequest? = null

        override suspend fun getUploadUrl(request: GetUploadUrlRequest): PresignedUrlResponse {
            uploadRequest = request
            return PresignedUrlResponse("https://bucket/put?sig=1", "media-1", 900, "")
        }

        override suspend fun getDownloadUrl(request: GetDownloadUrlRequest): PresignedUrlResponse = error("not used")

        override suspend fun confirmUpload(request: ConfirmUploadRequest): ConfirmUploadResponse {
            confirmed = request
            return ConfirmUploadResponse(request.mediaId)
        }
    }

    private class RecordingBlobStore : BlobStore {
        var bytes: ByteArray? = null
        var url: String? = null

        override suspend fun put(
            url: String,
            bytes: ByteArray,
            contentType: String,
            headers: Map<String, String>,
        ) {
            this.url = url
            this.bytes = bytes
        }
    }

    private val vaultClient = RecordingVaultClient()
    private val mediaClient = RecordingMediaClient()
    private val blobStore = RecordingBlobStore()
    private val storedKeys = mutableMapOf<String, ByteArray>()
    private val accessKeyStore =
        mockk<AccessKeyStore> {
            coEvery { store(any(), any(), any(), any()) } answers { storedKeys[firstArg()] = secondArg() }
            coEvery { getAndTouch(any()) } answers { storedKeys[firstArg()] }
            coEvery { delete(any()) } answers {
                storedKeys.remove(firstArg())
                Unit
            }
        }
    private val deviceSecrets = mockk<DeviceSecretProvider> { every { mediaAccessSecret() } returns ByteArray(32) { 9 } }
    private val repo = VaultRepositoryImpl(vaultClient, mediaClient, blobStore, accessKeyStore, deviceSecrets)

    private val plaintext = "the secret file".toByteArray()

    @Test
    fun `create uploads ciphertext only, seals the metadata, and keeps the key`() =
        runTest {
            val item = repo.createItem("passport.jpg", plaintext, "image/jpeg", thumbnailJpeg = byteArrayOf(1, 2, 3))

            // What left the device: an AES-GCM blob, never the file.
            val uploaded = requireNotNull(blobStore.bytes)
            assertFalse(uploaded.contentEquals(plaintext))
            assertEquals(12 + plaintext.size + 16, uploaded.size)
            val req = requireNotNull(mediaClient.uploadRequest)
            assertEquals(MediaPurpose.ATTACHMENT, req.purpose)
            assertEquals(uploaded.size.toLong(), req.fileSize)
            assertEquals(ConfirmUploadRequest("media-1", uploaded.size.toLong()), mediaClient.confirmed)

            // The server got an opaque envelope keyed by the item's own key; nothing descriptive in the clear.
            val created = requireNotNull(vaultClient.created)
            assertEquals("media-1", created.mediaId)
            assertFalse(String(created.encryptedMetadata, Charsets.ISO_8859_1).contains("passport"))
            val key = requireNotNull(storedKeys[created.vaultItemId])
            val metadata = VaultItemMetadata.decode(MediaEncryptor.openCombined(created.encryptedMetadata, key))
            assertEquals("passport.jpg", metadata.name)
            assertEquals("image/jpeg", metadata.mimeType)
            assertEquals(plaintext.size.toLong(), metadata.sizeBytes)
            assertEquals("vaultManual", metadata.kind)
            assertEquals("AQID", metadata.thumbnailJpeg)
            assertTrue(plaintext.contentEquals(MediaEncryptor.openCombined(uploaded, key)))

            assertEquals(VaultItemType.PHOTO, item.type)
            assertEquals(created.vaultItemId, item.id)
            assertNotNull(item.thumbnailJpeg)
        }

    @Test
    fun `listing decrypts items this device holds a key for and hides the rest`() =
        runTest {
            val mine = repo.createItem("notes.txt", "hello".toByteArray(), "text/plain")
            val minePb = requireNotNull(vaultClient.created)
            val sealed =
                VaultItem("other-device-item", "media-9", MediaEncryptor.sealCombined("{}".toByteArray(), ByteArray(32) { 5 }), 1L, 0L)
            vaultClient.page =
                GetVaultItemsResponse(
                    listOf(VaultItem(minePb.vaultItemId, "media-1", minePb.encryptedMetadata, 2_000L, 0L), sealed),
                    "cursor-2",
                )

            val page = repo.listItems()

            assertEquals(listOf(mine.id), page.items.map { it.id })
            assertEquals("notes.txt", page.items.single().name)
            assertEquals(VaultItemType.NOTE, page.items.single().type)
            assertEquals("cursor-2", page.nextCursor)
        }

    @Test
    fun `delete removes the server row and forgets the key`() =
        runTest {
            val item = repo.createItem("a.pdf", byteArrayOf(1), "application/pdf")
            repo.deleteItem(item.id)
            assertEquals(item.id, vaultClient.deleted)
            assertFalse(storedKeys.containsKey(item.id))
        }

    @Test
    fun `an oversized filename is truncated but keeps its extension`() {
        val long = "x".repeat(5000) + ".pdf"
        val out = VaultRepositoryImpl.truncateFileName(long, 4096)
        assertTrue(out.endsWith(".pdf"))
        assertTrue(out.toByteArray().size <= 4096)
        assertEquals("short.jpg", VaultRepositoryImpl.truncateFileName("short.jpg", 4096))
    }
}
