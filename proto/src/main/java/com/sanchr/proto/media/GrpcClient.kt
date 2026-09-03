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
 * once the media upload/download feature lands.
 */
class MediaServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : MediaServiceClient {
    // TODO(M6+): wire to MediaServiceGrpcKt.MediaServiceCoroutineStub when media upload lands
    override suspend fun getUploadUrl(request: GetUploadUrlRequest): PresignedUrlResponse =
        throw NotImplementedError("Awaiting media upload feature in M6+")

    // TODO(M6+): wire to MediaServiceGrpcKt.MediaServiceCoroutineStub when media upload lands
    override suspend fun getDownloadUrl(request: GetDownloadUrlRequest): PresignedUrlResponse =
        throw NotImplementedError("Awaiting media upload feature in M6+")

    // TODO(M6+): wire to MediaServiceGrpcKt.MediaServiceCoroutineStub when media upload lands
    override suspend fun confirmUpload(request: ConfirmUploadRequest): ConfirmUploadResponse =
        throw NotImplementedError("Awaiting media upload feature in M6+")
}
