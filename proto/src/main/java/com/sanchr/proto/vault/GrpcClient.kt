package com.sanchr.proto.vault

import io.grpc.CallOptions
import io.grpc.Channel

/**
 * gRPC client interface for the VaultService.
 * Generated stub equivalent for vync.vault.VaultService.
 */
interface VaultServiceClient {

    suspend fun getVaultItems(request: GetVaultItemsRequest): GetVaultItemsResponse

    suspend fun createVaultItem(request: CreateVaultItemRequest): VaultItem

    suspend fun deleteVaultItem(request: DeleteVaultItemRequest): DeleteVaultItemResponse

    suspend fun shareVaultItem(request: ShareVaultItemRequest): ShareVaultItemResponse
}

/**
 * Implementation shell that will delegate to the actual gRPC-generated stubs
 * once protobuf-gradle-plugin codegen runs.
 */
class VaultServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : VaultServiceClient {

    override suspend fun getVaultItems(request: GetVaultItemsRequest): GetVaultItemsResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun createVaultItem(request: CreateVaultItemRequest): VaultItem {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun deleteVaultItem(request: DeleteVaultItemRequest): DeleteVaultItemResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun shareVaultItem(request: ShareVaultItemRequest): ShareVaultItemResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }
}
