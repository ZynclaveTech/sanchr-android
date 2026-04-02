package com.sanchr.domain.vault

import com.sanchr.core.model.VaultItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for encrypted vault storage operations.
 */
interface VaultRepository {

    /** Observes all vault items. */
    fun observeVaultItems(): Flow<List<VaultItem>>

    /** Gets a single vault item by ID. */
    suspend fun getVaultItem(itemId: String): VaultItem?

    /** Creates a new encrypted vault item. */
    suspend fun createItem(name: String, data: ByteArray, mimeType: String): VaultItem

    /** Deletes a vault item and its encrypted data. */
    suspend fun deleteItem(itemId: String)

    /** Returns total vault storage used in bytes. */
    suspend fun getStorageUsed(): Long
}
