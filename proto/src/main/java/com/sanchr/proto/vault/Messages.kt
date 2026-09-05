package com.sanchr.proto.vault

/**
 * Wire models for `sanchr.vault.VaultService` (vault.proto). The server
 * never sees a filename, mime type, thumbnail or key: everything
 * descriptive travels inside [encryptedMetadata], AES-GCM under the
 * per-item access key the client derives and keeps.
 */
class CreateVaultItemRequest(
    /** Client-generated UUIDv4; retries with the same id are idempotent server-side. */
    val vaultItemId: String,
    /** A media object already uploaded via MediaService (GetUploadUrl + ConfirmUpload) and owned by the caller. */
    val mediaId: String,
    /** Opaque AES-GCM ciphertext of the metadata envelope. Non-empty, at most 64 KiB. */
    val encryptedMetadata: ByteArray,
    /** Unix millis, or 0 for no expiry. A lifecycle hint, not enforced cryptographically. */
    val expiresAt: Long = 0L,
)

class VaultItem(
    val vaultItemId: String,
    val mediaId: String,
    val encryptedMetadata: ByteArray,
    /** Server-stamped unix millis. */
    val createdAt: Long,
    val expiresAt: Long,
)

data class GetVaultItemsRequest(
    /** 1..100; the server defaults anything else to 20. */
    val limit: Int = DEFAULT_LIMIT,
    /** A previous response's [GetVaultItemsResponse.nextCursor]. */
    val pagingToken: String = "",
) {
    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 100
    }
}

class GetVaultItemsResponse(
    val items: List<VaultItem>,
    /** Empty when there is no further page. */
    val nextCursor: String,
)

data class GetVaultItemRequest(
    val vaultItemId: String,
)

data class DeleteVaultItemRequest(
    val vaultItemId: String,
)

data object DeleteVaultItemResponse
