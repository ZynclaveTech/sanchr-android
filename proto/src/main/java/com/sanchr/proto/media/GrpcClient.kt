package com.sanchr.proto.media

import io.grpc.CallOptions
import io.grpc.Channel
import sanchr.media.Media
import sanchr.media.MediaServiceGrpcKt

/**
 * gRPC client interface for the MediaService.
 * Adapter over the generated `sanchr.media.MediaServiceGrpcKt.MediaServiceCoroutineStub`.
 *
 * The service only brokers presigned URLs; the bytes go straight to object
 * storage over HTTP (see `AvatarUploader` in core:network).
 */
interface MediaServiceClient {
    suspend fun getUploadUrl(request: GetUploadUrlRequest): PresignedUrlResponse

    suspend fun getDownloadUrl(request: GetDownloadUrlRequest): PresignedUrlResponse

    suspend fun confirmUpload(request: ConfirmUploadRequest): ConfirmUploadResponse
}

class MediaServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : MediaServiceClient {
    private val stub by lazy { MediaServiceGrpcKt.MediaServiceCoroutineStub(channel, callOptions) }

    override suspend fun getUploadUrl(request: GetUploadUrlRequest): PresignedUrlResponse = stub.getUploadUrl(request.toProto()).toModel()

    override suspend fun getDownloadUrl(request: GetDownloadUrlRequest): PresignedUrlResponse =
        stub
            .getDownloadUrl(
                Media.GetDownloadUrlRequest
                    .newBuilder()
                    .setMediaId(request.mediaId)
                    .build(),
            ).toModel()

    override suspend fun confirmUpload(request: ConfirmUploadRequest): ConfirmUploadResponse =
        stub
            .confirmUpload(
                Media.ConfirmUploadRequest
                    .newBuilder()
                    .setMediaId(request.mediaId)
                    .setFileSize(request.fileSize)
                    .build(),
            ).let { ConfirmUploadResponse(mediaId = it.mediaId) }
}

internal fun MediaPurpose.toProto(): Media.MediaPurpose =
    when (this) {
        MediaPurpose.ATTACHMENT -> Media.MediaPurpose.MEDIA_PURPOSE_ATTACHMENT
        MediaPurpose.AVATAR -> Media.MediaPurpose.MEDIA_PURPOSE_AVATAR
    }

internal fun GetUploadUrlRequest.toProto(): Media.GetUploadUrlRequest =
    Media.GetUploadUrlRequest
        .newBuilder()
        .setFileSize(fileSize)
        .setContentType(contentType)
        .setSha256Hash(sha256Hex)
        .setPurpose(purpose.toProto())
        .build()

internal fun Media.PresignedUrlResponse.toModel(): PresignedUrlResponse =
    PresignedUrlResponse(
        url = url,
        mediaId = mediaId,
        expiresInSecs = expiresIn,
        displayUrl = displayUrl,
    )
