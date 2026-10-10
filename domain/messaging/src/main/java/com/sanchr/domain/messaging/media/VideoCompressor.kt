package com.sanchr.domain.messaging.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Re-encodes a picked video before it is sent, as iOS does.
 *
 * Android uploaded the file the camera produced: a minute of 4K is several
 * hundred megabytes, charged to the sender's data plan and the recipient's, and
 * stored at that size for both. iOS has capped the long edge at 720p — 540p in
 * Low Data Mode — on every send.
 *
 * [VideoCompressionPolicy] decides whether a clip is worth re-encoding at all;
 * this runs the transcode and, crucially, keeps the result only when it is
 * actually smaller. A transcode can produce something larger than its source,
 * and sending that would make the feature harmful rather than useless.
 */
@UnstableApi
@Singleton
class VideoCompressor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * A re-encoded copy of [source], or null to send the original as it is.
         *
         * Never throws: a failed transcode falls back to the original, because
         * a video the user chose to send matters more than sending it smaller.
         * The caller owns the returned file and should delete it once the bytes
         * are read.
         */
        suspend fun compressedForSending(
            source: Uri,
            sourceBytes: Long,
            lowData: Boolean,
        ): File? {
            val size = sizeOf(source)
            val decision = VideoCompressionPolicy.decide(size.width, size.height, sourceBytes, lowData)
            if (decision is VideoCompressionPolicy.Decision.SendOriginal) {
                Log.d(TAG, "sending original: " + decision.reason)
                return null
            }
            val longEdge = (decision as VideoCompressionPolicy.Decision.Compress).longEdge
            val outputSize = targetDimensions(size, longEdge)
            val target = File(context.cacheDir, "compressed-${System.currentTimeMillis()}.mp4")
            val exported =
                try {
                    transcode(source, target, outputSize)
                } catch (e: Exception) {
                    Log.w(TAG, "transcode failed; sending the original", e)
                    false
                }
            if (!exported || !target.isFile) {
                target.delete()
                return null
            }
            if (!VideoCompressionPolicy.isWorthKeeping(target.length(), sourceBytes)) {
                // Bigger, or barely smaller: the original wins.
                Log.d(TAG, "transcode saved too little (" + target.length() + " vs " + sourceBytes + ")")
                target.delete()
                return null
            }
            return target
        }

        /** Dimensions of the clip at [uri], or zeroes when they cannot be read. */
        private fun sizeOf(uri: Uri): Dimensions {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, uri)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                // Swapped for a quarter-turn: the stored width and height are
                // pre-rotation, so a portrait clip reports landscape dimensions
                // and would have been scaled against the wrong edge.
                if (rotation == QUARTER_TURN || rotation == THREE_QUARTER_TURN) {
                    Dimensions(width = height, height = width)
                } else {
                    Dimensions(width = width, height = height)
                }
            } catch (e: Exception) {
                Log.w(TAG, "could not read video dimensions", e)
                Dimensions(0, 0)
            } finally {
                runCatching { retriever.release() }
            }
        }

        /**
         * Runs the export. Returns whether it completed.
         *
         * `Transformer` must be built and started on a thread with a Looper and
         * reports back on that thread, so the whole call runs on the main
         * dispatcher while the work itself happens on the encoder's threads.
         */
        private suspend fun transcode(
            source: Uri,
            target: File,
            output: Dimensions,
        ): Boolean =
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val transformer =
                        Transformer
                            .Builder(context)
                            .addListener(
                                object : Transformer.Listener {
                                    override fun onCompleted(
                                        composition: Composition,
                                        result: ExportResult,
                                    ) {
                                        if (continuation.isActive) continuation.resume(true)
                                    }

                                    override fun onError(
                                        composition: Composition,
                                        result: ExportResult,
                                        exception: ExportException,
                                    ) {
                                        Log.w(TAG, "export failed", exception)
                                        if (continuation.isActive) continuation.resume(false)
                                    }
                                },
                            ).build()
                    val edited =
                        EditedMediaItem
                            .Builder(MediaItem.fromUri(source))
                            // An explicit size rather than `createForHeight`:
                            // that caps the height, which is the *long* edge of
                            // a portrait clip, so a phone video held upright
                            // would have been shrunk to the target on its long
                            // side and then again on its short one. The size
                            // passed here already matches the source's aspect
                            // ratio, so SCALE_TO_FIT adds no letterboxing.
                            .setEffects(
                                Effects(
                                    emptyList(),
                                    listOf(
                                        Presentation.createForWidthAndHeight(
                                            output.width,
                                            output.height,
                                            Presentation.LAYOUT_SCALE_TO_FIT,
                                        ),
                                    ),
                                ),
                            ).build()
                    continuation.invokeOnCancellation { runCatching { transformer.cancel() } }
                    transformer.start(edited, target.path)
                }
            }

        /**
         * The output size: the source scaled so its longer edge is [longEdge],
         * keeping the aspect ratio, with both edges even.
         *
         * Encoders reject odd dimensions for most profiles, so rounding down to
         * even is not cosmetic.
         */
        internal fun targetDimensions(
            source: Dimensions,
            longEdge: Int,
        ): Dimensions {
            val sourceLong = maxOf(source.width, source.height)
            if (sourceLong <= 0) return source
            val scale = longEdge.toDouble() / sourceLong
            return Dimensions(
                width = even((source.width * scale).toInt()),
                height = even((source.height * scale).toInt()),
            )
        }

        private fun even(value: Int): Int = (value - value % 2).coerceAtLeast(2)

        /** Pixel dimensions after any rotation has been applied. */
        internal data class Dimensions(
            val width: Int,
            val height: Int,
        )

        private companion object {
            const val TAG = "VideoCompressor"

            const val QUARTER_TURN = 90
            const val THREE_QUARTER_TURN = 270
        }
    }
