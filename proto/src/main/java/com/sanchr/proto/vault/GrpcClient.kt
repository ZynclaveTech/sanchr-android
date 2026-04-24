package com.sanchr.proto.vault

import io.grpc.CallOptions
import io.grpc.Channel

/**
 * gRPC client interface for the VaultService.
 * Generated stub equivalent for sanchr.vault.VaultService.
 */
interface VaultServiceClient {
    suspend fun getVaultItems(request: GetVaultItemsRequest): GetVaultItemsResponse

    suspend fun createVaultItem(request: CreateVaultItemRequest): VaultItem

    suspend fun deleteVaultItem(request: DeleteVaultItemRequest): DeleteVaultItemResponse

    suspend fun shareVaultItem(request: ShareVaultItemRequest): ShareVaultItemResponse
}

/**
 * Implementation shell that will delegate to the actual gRPC-generated stubs
 * once the vault feature lands.
 */
class VaultServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : VaultServiceClient {
    // TODO(M6+): wire to VaultServiceGrpcKt.VaultServiceCoroutineStub when vault lands
    override suspend fun getVaultItems(request: GetVaultItemsRequest): GetVaultItemsResponse =
        throw NotImplementedError("Awaiting vault feature in M6+")

    // TODO(M6+): wire to VaultServiceGrpcKt.VaultServiceCoroutineStub when vault lands
    override suspend fun createVaultItem(request: CreateVaultItemRequest): VaultItem =
        throw NotImplementedError("Awaiting vault feature in M6+")

    // TODO(M6+): wire to VaultServiceGrpcKt.VaultServiceCoroutineStub when vault lands
    override suspend fun deleteVaultItem(request: DeleteVaultItemRequest): DeleteVaultItemResponse =
        throw NotImplementedError("Awaiting vault feature in M6+")

    // TODO(M6+): wire to VaultServiceGrpcKt.VaultServiceCoroutineStub when vault lands
    override suspend fun shareVaultItem(request: ShareVaultItemRequest): ShareVaultItemResponse =
        throw NotImplementedError("Awaiting vault feature in M6+")
}
