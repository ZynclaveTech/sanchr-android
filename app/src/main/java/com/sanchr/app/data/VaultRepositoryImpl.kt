package com.sanchr.app.data

import com.sanchr.core.model.VaultItem
import com.sanchr.core.model.VaultItemType
import com.sanchr.domain.vault.VaultRepository
import com.sanchr.proto.vault.CreateVaultItemRequest
import com.sanchr.proto.vault.DeleteVaultItemRequest
import com.sanchr.proto.vault.GetVaultItemsRequest
import com.sanchr.proto.vault.VaultServiceClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton
import com.sanchr.proto.vault.VaultItem as ProtoVaultItem

@Singleton
class VaultRepositoryImpl @Inject constructor(
    private val vaultClient: VaultServiceClient,
) : VaultRepository {

    override fun observeVaultItems(): Flow<List<VaultItem>> = flow {
        val response = vaultClient.getVaultItems(GetVaultItemsRequest())
        emit(response.items.map { it.toDomain() })
    }

    override suspend fun getVaultItem(itemId: String): VaultItem? {
        val response = vaultClient.getVaultItems(GetVaultItemsRequest())
        return response.items.firstOrNull { it.id == itemId }?.toDomain()
    }

    override suspend fun createItem(name: String, data: ByteArray, mimeType: String): VaultItem {
        val category = mimeTypeToCategory(mimeType)
        val protoItem = vaultClient.createVaultItem(
            CreateVaultItemRequest(
                title = name,
                category = category,
                encryptedContent = data,
                contentType = mimeType,
            ),
        )
        return protoItem.toDomain()
    }

    override suspend fun deleteItem(itemId: String) {
        vaultClient.deleteVaultItem(DeleteVaultItemRequest(itemId = itemId))
    }

    override suspend fun getStorageUsed(): Long {
        val response = vaultClient.getVaultItems(GetVaultItemsRequest())
        return response.items.sumOf { it.sizeBytes }
    }

    // ── Mapping helpers ──

    private fun ProtoVaultItem.toDomain(): VaultItem = VaultItem(
        id = id,
        ownerId = "", // Server does not expose owner in list response
        type = categoryToVaultItemType(category),
        name = title,
        mimeType = contentType.ifEmpty { null },
        sizeBytes = sizeBytes,
        encryptedUri = "", // URI resolved at download time
        thumbnailUri = thumbnailUrl.ifEmpty { null },
        tags = tags,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        modifiedAt = Instant.fromEpochMilliseconds(updatedAt),
    )

    private fun categoryToVaultItemType(category: String): VaultItemType = when (category.lowercase()) {
        "photo" -> VaultItemType.PHOTO
        "video" -> VaultItemType.VIDEO
        "document" -> VaultItemType.DOCUMENT
        "note" -> VaultItemType.NOTE
        "password" -> VaultItemType.PASSWORD
        else -> VaultItemType.DOCUMENT
    }

    private fun mimeTypeToCategory(mimeType: String): String = when {
        mimeType.startsWith("image/") -> "photo"
        mimeType.startsWith("video/") -> "video"
        else -> "document"
    }
}
