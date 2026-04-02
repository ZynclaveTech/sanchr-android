package com.sanchr.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class VaultItem(
    val id: String,
    val ownerId: String,
    val type: VaultItemType,
    val name: String,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val encryptedUri: String,
    val thumbnailUri: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Instant,
    val modifiedAt: Instant,
)

@Serializable
enum class VaultItemType {
    PHOTO,
    VIDEO,
    DOCUMENT,
    NOTE,
    PASSWORD,
}
