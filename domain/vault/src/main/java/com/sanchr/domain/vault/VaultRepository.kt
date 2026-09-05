package com.sanchr.domain.vault

import com.sanchr.core.model.VaultItem

/** One page of the vault, already decrypted; sealed items are omitted. */
data class VaultPage(
    val items: List<VaultItem>,
    /** Empty when there is no further page. */
    val nextCursor: String,
)

/**
 * The forward-secure vault. Everything descriptive about an item is
 * encrypted on this device under a per-item key the server never sees;
 * the implementation owns that key material.
 */
interface VaultRepository {
    suspend fun listItems(
        limit: Int = DEFAULT_PAGE_SIZE,
        cursor: String = "",
    ): VaultPage

    /**
     * Encrypts [data] and its metadata, uploads the ciphertext, registers
     * the item, and keeps the key locally. [thumbnailJpeg] is a small
     * preview generated on the device (it is encrypted too).
     */
    suspend fun createItem(
        name: String,
        data: ByteArray,
        mimeType: String,
        thumbnailJpeg: ByteArray? = null,
    ): VaultItem

    suspend fun deleteItem(itemId: String)

    companion object {
        const val DEFAULT_PAGE_SIZE = 30
    }
}
