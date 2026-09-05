package com.sanchr.app.data

import android.util.Log
import com.sanchr.app.data.vault.VaultItemMetadata
import com.sanchr.core.crypto.AccessKeyStore
import com.sanchr.core.crypto.DeviceSecretProvider
import com.sanchr.core.crypto.MediaEncryptor
import com.sanchr.core.crypto.VaultKeyDerivation
import com.sanchr.core.model.VaultItem
import com.sanchr.core.model.VaultItemType
import com.sanchr.core.network.media.AvatarUploader
import com.sanchr.core.network.media.BlobStore
import com.sanchr.domain.vault.VaultPage
import com.sanchr.domain.vault.VaultRepository
import com.sanchr.proto.media.ConfirmUploadRequest
import com.sanchr.proto.media.GetDownloadUrlRequest
import com.sanchr.proto.media.GetUploadUrlRequest
import com.sanchr.proto.media.MediaPurpose
import com.sanchr.proto.media.MediaServiceClient
import com.sanchr.proto.vault.CreateVaultItemRequest
import com.sanchr.proto.vault.DeleteVaultItemRequest
import com.sanchr.proto.vault.GetVaultItemsRequest
import com.sanchr.proto.vault.VaultItem as ProtoVaultItem
import com.sanchr.proto.vault.VaultServiceClient
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant

class VaultException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The manual vault path, step for step as iOS `VaultDataSource.createVaultItem`:
 *
 *  1. fresh `vault_item_id` (UUIDv4) and 32-byte salt
 *  2. `AccessK_vault = HKDF(device media-access secret, salt, "sanchr-vault-manual-v1-<id>")`
 *  3. payload sealed with AES-GCM under that key (`nonce || ct || tag`)
 *  4. `GetUploadUrl(ATTACHMENT, sha256(ciphertext))` → PUT → `ConfirmUpload`
 *  5. metadata envelope (name, mime, size, thumbnail, …) sealed under the same key, ≤ 64 KiB
 *  6. `CreateVaultItem(id, media_id, encrypted_metadata)`
 *  7. key kept in [AccessKeyStore]
 *
 * Reading a page decrypts each envelope under the stored key; an item
 * whose key this device does not hold is sealed and skipped.
 */
@Singleton
class VaultRepositoryImpl
    @Inject
    constructor(
        private val vaultClient: VaultServiceClient,
        private val mediaClient: MediaServiceClient,
        private val blobStore: BlobStore,
        private val accessKeyStore: AccessKeyStore,
        private val deviceSecretProvider: DeviceSecretProvider,
    ) : VaultRepository {
        override suspend fun listItems(
            limit: Int,
            cursor: String,
        ): VaultPage {
            val response = vaultClient.getVaultItems(GetVaultItemsRequest(limit = limit, pagingToken = cursor))
            var sealed = 0
            val items =
                response.items.mapNotNull { proto ->
                    val key = accessKeyStore.getAndTouch(proto.vaultItemId)
                    if (key == null) {
                        sealed++
                        return@mapNotNull null
                    }
                    try {
                        proto.toDomain(VaultItemMetadata.decode(MediaEncryptor.openCombined(proto.encryptedMetadata, key)))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "vault item ${proto.vaultItemId} could not be decrypted; skipping", e)
                        sealed++
                        null
                    }
                }
            if (sealed > 0) Log.i(TAG, "$sealed sealed vault item(s) hidden")
            return VaultPage(items, response.nextCursor)
        }

        override suspend fun createItem(
            name: String,
            data: ByteArray,
            mimeType: String,
            thumbnailJpeg: ByteArray?,
        ): VaultItem {
            require(data.isNotEmpty()) { "vault item is empty" }
            val vaultItemId = UUID.randomUUID().toString().lowercase()
            val salt = ByteArray(VaultKeyDerivation.SALT_SIZE).also { SecureRandom().nextBytes(it) }
            val accessKey = VaultKeyDerivation.deriveManual(deviceSecretProvider.mediaAccessSecret(), salt, vaultItemId)

            val ciphertext = MediaEncryptor.sealCombined(data, accessKey)
            val contentType = mimeType.ifBlank { OCTET_STREAM }
            val presigned =
                mediaClient.getUploadUrl(
                    GetUploadUrlRequest(
                        fileSize = ciphertext.size.toLong(),
                        contentType = contentType,
                        sha256Hex = AvatarUploader.sha256Hex(ciphertext),
                        purpose = MediaPurpose.ATTACHMENT,
                    ),
                )
            blobStore.put(presigned.url, ciphertext, contentType)
            mediaClient.confirmUpload(ConfirmUploadRequest(mediaId = presigned.mediaId, fileSize = ciphertext.size.toLong()))

            val metadata =
                VaultItemMetadata(
                    name = truncateFileName(name, MAX_NAME_BYTES),
                    mimeType = contentType,
                    sizeBytes = data.size.toLong(),
                    thumbnailJpeg =
                        thumbnailJpeg?.takeIf { it.size <= MAX_THUMBNAIL_BYTES }?.let {
                            Base64.getEncoder().encodeToString(
                                it,
                            )
                        },
                    createdAtMs = System.currentTimeMillis(),
                    kind = AccessKeyStore.Kind.VAULT_MANUAL.wire,
                )
            val metadataJson = metadata.encode()
            if (metadataJson.size > MAX_METADATA_BYTES) throw VaultException("vault metadata too large: ${metadataJson.size} bytes")
            val encryptedMetadata = MediaEncryptor.sealCombined(metadataJson, accessKey)

            val created =
                vaultClient.createVaultItem(
                    CreateVaultItemRequest(vaultItemId = vaultItemId, mediaId = presigned.mediaId, encryptedMetadata = encryptedMetadata),
                )
            accessKeyStore.store(vaultItemId, accessKey, AccessKeyStore.Kind.VAULT_MANUAL)
            Log.i(TAG, "vault item $vaultItemId created (${data.size} bytes)")
            return created.toDomain(metadata)
        }

        override suspend fun deleteItem(itemId: String) {
            vaultClient.deleteVaultItem(DeleteVaultItemRequest(itemId))
            accessKeyStore.delete(itemId)
        }

        override suspend fun download(item: VaultItem): ByteArray {
            val key = accessKeyStore.getAndTouch(item.id) ?: throw VaultException("vault item ${item.id} is sealed on this device")
            val presigned = mediaClient.getDownloadUrl(GetDownloadUrlRequest(item.mediaId))
            val ciphertext = blobStore.get(presigned.url)
            return try {
                MediaEncryptor.openCombined(ciphertext, key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw VaultException("vault item ${item.id} failed to decrypt", e)
            }
        }

        private fun ProtoVaultItem.toDomain(metadata: VaultItemMetadata): VaultItem =
            VaultItem(
                id = vaultItemId,
                mediaId = mediaId,
                type = VaultItemType.fromMimeType(metadata.mimeType),
                name = metadata.name,
                mimeType = metadata.mimeType,
                sizeBytes = metadata.sizeBytes,
                thumbnailJpeg = metadata.thumbnailJpeg?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() },
                createdAt = Instant.fromEpochMilliseconds(createdAt.takeIf { it > 0 } ?: metadata.createdAtMs),
                expiresAt = expiresAt.takeIf { it > 0 }?.let { Instant.fromEpochMilliseconds(it) },
            )

        companion object {
            private const val TAG = "VaultRepository"
            private const val OCTET_STREAM = "application/octet-stream"
            const val MAX_METADATA_BYTES = 64 * 1024
            const val MAX_NAME_BYTES = 4 * 1024
            const val MAX_THUMBNAIL_BYTES = 48 * 1024

            /** Keeps the extension, as iOS `truncateFileName`: a document picker can hand back a huge opaque name. */
            fun truncateFileName(
                name: String,
                maxBytes: Int,
            ): String {
                if (name.toByteArray(Charsets.UTF_8).size <= maxBytes) return name
                val dot = name.lastIndexOf('.')
                val ext = if (dot in 1 until name.length - 1 && name.length - dot <= MAX_EXT_CHARS) name.substring(dot) else ""
                var base = name.removeSuffix(ext)
                while (base.isNotEmpty() && (base + ext).toByteArray(Charsets.UTF_8).size > maxBytes) base = base.dropLast(1)
                return base + ext
            }

            private const val MAX_EXT_CHARS = 16
        }
    }
