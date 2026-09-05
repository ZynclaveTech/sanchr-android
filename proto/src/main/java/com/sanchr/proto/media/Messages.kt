package com.sanchr.proto.media

/** Mirrors `sanchr.media.MediaPurpose`: controls the object's ACL and storage path. */
enum class MediaPurpose {
    /** E2EE message attachment — private, presigned access only. */
    ATTACHMENT,

    /** Profile avatar — public-read via the CDN. */
    AVATAR,
}

data class GetUploadUrlRequest(
    val fileSize: Long,
    /** MIME type of the bytes that will be PUT, e.g. `image/jpeg`. */
    val contentType: String,
    /** Hex SHA-256 of the bytes that will be PUT (the *encrypted* blob for attachments); the server dedups on it. */
    val sha256Hex: String = "",
    val purpose: MediaPurpose = MediaPurpose.ATTACHMENT,
)

data class GetDownloadUrlRequest(
    val mediaId: String,
)

data class PresignedUrlResponse(
    /** Presigned PUT (upload) or GET (download) URL. */
    val url: String,
    val mediaId: String,
    /** Seconds until [url] stops working. */
    val expiresInSecs: Long,
    /** Permanent CDN URL for display; empty when no CDN is configured, and may be a relative path. */
    val displayUrl: String,
)

data class ConfirmUploadRequest(
    val mediaId: String,
    val fileSize: Long,
)

data class ConfirmUploadResponse(
    val mediaId: String,
)
