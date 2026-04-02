package com.sanchr.proto.vault

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetVaultItemsRequest(
    @SerialName("page_token") val pageToken: String = "",
    @SerialName("page_size") val pageSize: Int = 50,
    val category: String = "",
)

@Serializable
data class GetVaultItemsResponse(
    val items: List<VaultItem> = emptyList(),
    @SerialName("next_page_token") val nextPageToken: String = "",
)

@Serializable
data class CreateVaultItemRequest(
    val title: String = "",
    val category: String = "",
    @SerialName("encrypted_content") val encryptedContent: ByteArray = ByteArray(0),
    @SerialName("content_type") val contentType: String = "",
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    val tags: List<String> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CreateVaultItemRequest) return false
        return title == other.title && encryptedContent.contentEquals(other.encryptedContent)
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + encryptedContent.contentHashCode()
        return result
    }
}

@Serializable
data class VaultItem(
    val id: String = "",
    val title: String = "",
    val category: String = "",
    @SerialName("content_type") val contentType: String = "",
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    val tags: List<String> = emptyList(),
    @SerialName("size_bytes") val sizeBytes: Long = 0L,
    @SerialName("shared_with") val sharedWith: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("updated_at") val updatedAt: Long = 0L,
)

@Serializable
data class DeleteVaultItemRequest(
    @SerialName("item_id") val itemId: String = "",
)

@Serializable
data class DeleteVaultItemResponse(
    val success: Boolean = false,
)

@Serializable
data class ShareVaultItemRequest(
    @SerialName("item_id") val itemId: String = "",
    @SerialName("recipient_user_ids") val recipientUserIds: List<String> = emptyList(),
)

@Serializable
data class ShareVaultItemResponse(
    val success: Boolean = false,
    @SerialName("shared_count") val sharedCount: Int = 0,
)
