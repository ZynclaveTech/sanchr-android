package com.sanchr.proto.media

import io.grpc.CallOptions
import io.grpc.Channel

/**
 * gRPC client interface for the MediaService.
 * Generated stub equivalent for sanchr.media.MediaService.
 */
interface MediaServiceClient {

    suspend fun getUploadUrl(request: GetUploadUrlRequest): PresignedUrlResponse

    suspend fun getDownloadUrl(request: GetDownloadUrlRequest): PresignedUrlResponse

    suspend fun confirmUpload(request: ConfirmUploadRequest): ConfirmUploadResponse
}

/**
 * Implementation shell that will delegate to the actual gRPC-generated stubs
 * once protobuf-gradle-plugin codegen runs.
 */
class MediaServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : MediaServiceClient {

    override suspend fun getUploadUrl(request: GetUploadUrlRequest): PresignedUrlResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun getDownloadUrl(request: GetDownloadUrlRequest): PresignedUrlResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun confirmUpload(request: ConfirmUploadRequest): ConfirmUploadResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }
}
