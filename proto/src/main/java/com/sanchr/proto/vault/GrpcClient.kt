package com.sanchr.proto.vault

import com.google.protobuf.ByteString
import io.grpc.CallOptions
import io.grpc.Channel
import sanchr.vault.Vault
import sanchr.vault.VaultServiceGrpcKt

/**
 * gRPC client interface for the VaultService.
 * Adapter over the generated `sanchr.vault.VaultServiceGrpcKt.VaultServiceCoroutineStub`.
 *
 * Sharing is deliberately absent from the wire ("will return in a future
 * round with a proper cryptographic re-wrap design", vault.proto).
 */
interface VaultServiceClient {
    suspend fun createVaultItem(request: CreateVaultItemRequest): VaultItem

    suspend fun getVaultItems(request: GetVaultItemsRequest): GetVaultItemsResponse

    suspend fun getVaultItem(request: GetVaultItemRequest): VaultItem

    suspend fun deleteVaultItem(request: DeleteVaultItemRequest): DeleteVaultItemResponse
}

class VaultServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : VaultServiceClient {
    private val stub by lazy { VaultServiceGrpcKt.VaultServiceCoroutineStub(channel, callOptions) }

    override suspend fun createVaultItem(request: CreateVaultItemRequest): VaultItem = stub.createVaultItem(request.toProto()).toModel()

    override suspend fun getVaultItems(request: GetVaultItemsRequest): GetVaultItemsResponse =
        stub
            .getVaultItems(
                Vault.GetVaultItemsRequest
                    .newBuilder()
                    .setLimit(request.limit)
                    .setPagingToken(request.pagingToken)
                    .build(),
            ).let { GetVaultItemsResponse(items = it.itemsList.map { item -> item.toModel() }, nextCursor = it.nextCursor) }

    override suspend fun getVaultItem(request: GetVaultItemRequest): VaultItem =
        stub
            .getVaultItem(
                Vault.GetVaultItemRequest
                    .newBuilder()
                    .setVaultItemId(request.vaultItemId)
                    .build(),
            ).toModel()

    override suspend fun deleteVaultItem(request: DeleteVaultItemRequest): DeleteVaultItemResponse {
        stub.deleteVaultItem(
            Vault.DeleteVaultItemRequest
                .newBuilder()
                .setVaultItemId(request.vaultItemId)
                .build(),
        )
        return DeleteVaultItemResponse
    }
}

internal fun CreateVaultItemRequest.toProto(): Vault.CreateVaultItemRequest =
    Vault.CreateVaultItemRequest
        .newBuilder()
        .setVaultItemId(vaultItemId)
        .setMediaId(mediaId)
        .setEncryptedMetadata(ByteString.copyFrom(encryptedMetadata))
        .setExpiresAt(expiresAt)
        .build()

internal fun Vault.VaultItem.toModel(): VaultItem =
    VaultItem(
        vaultItemId = vaultItemId,
        mediaId = mediaId,
        encryptedMetadata = encryptedMetadata.toByteArray(),
        createdAt = createdAt,
        expiresAt = expiresAt,
    )
