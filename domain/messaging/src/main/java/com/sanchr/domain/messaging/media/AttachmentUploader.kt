package com.sanchr.domain.messaging.media

import com.sanchr.core.crypto.MediaEncryptor
import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.network.media.AvatarUploader
import com.sanchr.core.network.media.BlobStore
import com.sanchr.proto.media.ConfirmUploadRequest
import com.sanchr.proto.media.GetUploadUrlRequest
import com.sanchr.proto.media.MediaPurpose
import com.sanchr.proto.media.MediaServiceClient
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts an attachment under a fresh random key and puts the ciphertext
 * in object storage, the way iOS `MediaUploadManager` does: AES-GCM,
 * `nonce || ct || tag` (chunked past 1 MiB), `GetUploadUrl(ATTACHMENT,
 * sha256(ciphertext))`, PUT, `ConfirmUpload`. The key never leaves the
 * device except inside the end-to-end encrypted message that references
 * the upload.
 */
@Singleton
class AttachmentUploader
    @Inject
    constructor(
        private val mediaClient: MediaServiceClient,
        private val blobStore: BlobStore,
    ) {
        class Prepared(
            val bytes: ByteArray,
            val mimeType: String,
            val fileName: String?,
            val caption: String? = null,
            val width: Int? = null,
            val height: Int? = null,
            val durationSeconds: Double? = null,
            val isVoiceMessage: Boolean? = null,
            val audioDurationMs: Int? = null,
            /** Normalised 0..1 loudness buckets for the voice-note waveform, as iOS `audioWaveform`. */
            val audioWaveform: List<Float>? = null,
            /** Placeholder hash for images/video posters, as iOS `blurHash`. */
            val blurHash: String? = null,
            /** The recipient may open it once; their client then wipes it (iOS `isViewOnce`). */
            val isViewOnce: Boolean? = null,
        ) {
            /**
             * The same file with [caption] attached. Used by the share sheet,
             * where the caption is typed after the file has been read.
             */
            fun withCaption(caption: String?): Prepared =
                Prepared(
                    bytes,
                    mimeType,
                    fileName,
                    caption,
                    width,
                    height,
                    durationSeconds,
                    isVoiceMessage,
                    audioDurationMs,
                    audioWaveform,
                    blurHash,
                    isViewOnce,
                )

            fun asViewOnce(): Prepared =
                Prepared(
                    bytes,
                    mimeType,
                    fileName,
                    caption,
                    width,
                    height,
                    durationSeconds,
                    isVoiceMessage,
                    audioDurationMs,
                    audioWaveform,
                    blurHash,
                    isViewOnce = true,
                )
        }

        suspend fun upload(
            prepared: Prepared,
            /** Fraction of the encrypted body written, 0..1. */
            onProgress: ((Float) -> Unit)? = null,
        ): MediaAttachment {
            require(prepared.bytes.isNotEmpty()) { "attachment is empty" }
            val key = MediaEncryptor.generateMediaKey()
            val ciphertext = MediaEncryptor.sealAny(prepared.bytes, key)
            val contentType = prepared.mimeType.ifBlank { OCTET_STREAM }
            val presigned =
                mediaClient.getUploadUrl(
                    GetUploadUrlRequest(
                        fileSize = ciphertext.size.toLong(),
                        contentType = contentType,
                        sha256Hex = AvatarUploader.sha256Hex(ciphertext),
                        purpose = MediaPurpose.ATTACHMENT,
                    ),
                )
            blobStore.put(presigned.url, ciphertext, contentType) { sent, total ->
                if (onProgress != null && total > 0) onProgress((sent.toFloat() / total).coerceIn(0f, 1f))
            }
            mediaClient.confirmUpload(ConfirmUploadRequest(mediaId = presigned.mediaId, fileSize = ciphertext.size.toLong()))
            val nonce = ciphertext.copyOfRange(0, NONCE_SIZE)
            return MediaAttachment(
                url = MediaAttachment.mediaUrl(presigned.mediaId),
                encryptionKey = Base64.getEncoder().encodeToString(key),
                encryptionIV = Base64.getEncoder().encodeToString(nonce),
                mimeType = contentType,
                sizeBytes = prepared.bytes.size.toLong(),
                caption = prepared.caption,
                width = prepared.width,
                height = prepared.height,
                durationSeconds = prepared.durationSeconds,
                filename = prepared.fileName,
                isVoiceMessage = prepared.isVoiceMessage,
                audioDurationMs = prepared.audioDurationMs,
                audioWaveform = prepared.audioWaveform,
                blurHash = prepared.blurHash,
                isViewOnce = prepared.isViewOnce,
            )
        }

        private companion object {
            const val OCTET_STREAM = "application/octet-stream"
            const val NONCE_SIZE = 12
        }
    }
