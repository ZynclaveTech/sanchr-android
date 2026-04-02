package com.sanchr.proto.keys

import io.grpc.CallOptions
import io.grpc.Channel

/**
 * gRPC client interface for the KeyService.
 * Generated stub equivalent for vync.keys.KeyService.
 */
interface KeyServiceClient {

    suspend fun uploadKeyBundle(request: KeyBundle): UploadKeyBundleResponse

    suspend fun getPreKeyBundle(request: GetPreKeyBundleRequest): PreKeyBundleResponse

    suspend fun uploadOneTimePreKeys(request: UploadOneTimePreKeysRequest): UploadKeyBundleResponse

    suspend fun getPreKeyCount(request: GetPreKeyCountRequest): PreKeyCountResponse

    suspend fun getUserDevices(request: GetUserDevicesRequest): GetUserDevicesResponse
}

/**
 * Implementation shell that will delegate to the actual gRPC-generated stubs
 * once protobuf-gradle-plugin codegen runs.
 */
class KeyServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : KeyServiceClient {

    override suspend fun uploadKeyBundle(request: KeyBundle): UploadKeyBundleResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun getPreKeyBundle(request: GetPreKeyBundleRequest): PreKeyBundleResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun uploadOneTimePreKeys(
        request: UploadOneTimePreKeysRequest,
    ): UploadKeyBundleResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun getPreKeyCount(request: GetPreKeyCountRequest): PreKeyCountResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun getUserDevices(request: GetUserDevicesRequest): GetUserDevicesResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }
}
