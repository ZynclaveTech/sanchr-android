package com.sanchr.proto.media

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetUploadUrlRequest(
    @SerialName("file_name") val fileName: String = "",
    @SerialName("content_type") val contentType: String = "",
    @SerialName("size_bytes") val sizeBytes: Long = 0L,
    val purpose: String = "",
)

@Serializable
data class GetDownloadUrlRequest(
    @SerialName("media_id") val mediaId: String = "",
)

@Serializable
data class PresignedUrlResponse(
    val url: String = "",
    @SerialName("media_id") val mediaId: String = "",
    @SerialName("expires_at") val expiresAt: Long = 0L,
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class ConfirmUploadRequest(
    @SerialName("media_id") val mediaId: String = "",
    @SerialName("size_bytes") val sizeBytes: Long = 0L,
    @SerialName("checksum_sha256") val checksumSha256: String = "",
)

@Serializable
data class ConfirmUploadResponse(
    val success: Boolean = false,
    @SerialName("media_url") val mediaUrl: String = "",
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
)
