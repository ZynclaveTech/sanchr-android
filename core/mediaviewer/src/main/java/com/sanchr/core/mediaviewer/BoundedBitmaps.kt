package com.sanchr.core.mediaviewer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Decoding a bitmap without letting the file choose the allocation.
 *
 * `BitmapFactory.decode*` allocates from the image's own dimensions, which is
 * fine until the image is large — and images here are either sent by someone
 * else or come from a camera roll. A 108-megapixel photo is 432 MB as
 * ARGB_8888 and a deliberately crafted PNG can be far worse while compressing
 * to almost nothing. Both end the process the same way.
 *
 * Reading the header first costs nothing and turns that into a downsample.
 * One place for the rule, because a second decoder without it puts the whole
 * thing back.
 */
object BoundedBitmaps {
    /** Ceiling for a decoded bitmap. Above this the image is halved until it fits. */
    const val DEFAULT_MAX_BYTES = 64L * 1024 * 1024

    private const val BYTES_PER_PIXEL = 4

    fun decode(
        file: File,
        maxBytes: Long = DEFAULT_MAX_BYTES,
    ): Bitmap? =
        decode(maxBytes) { options ->
            BitmapFactory.decodeFile(file.path, options)
        }

    fun decode(
        resolver: ContentResolver,
        uri: Uri,
        maxBytes: Long = DEFAULT_MAX_BYTES,
    ): Bitmap? =
        decode(maxBytes) { options ->
            // Opened afresh for each pass: a stream cannot be rewound after
            // the bounds pass has consumed its header.
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }

    /**
     * The sample size that brings an image of [width] x [height] under
     * [maxBytes].
     *
     * Always a power of two, which is what BitmapFactory honours — it rounds
     * anything else down to one, and a rejected sample size is the same as no
     * limit at all.
     */
    fun sampleSizeFor(
        width: Int,
        height: Int,
        maxBytes: Long = DEFAULT_MAX_BYTES,
    ): Int {
        if (width <= 0 || height <= 0) return 1
        // Compared as pixels, not bytes. Multiplying two Int.MAX_VALUE sides
        // and then by four overflows Long and comes out negative, so the
        // check passed and the largest images — the ones this exists for —
        // were the ones that went through undownsampled.
        val maxPixels = (maxBytes / BYTES_PER_PIXEL).coerceAtLeast(1)
        var sample = 1
        while (
            (width.toLong() / sample) * (height.toLong() / sample) > maxPixels &&
            sample < MAX_SAMPLE
        ) {
            sample *= 2
        }
        return sample
    }

    private const val MAX_SAMPLE = 1 shl 10

    private inline fun decode(
        maxBytes: Long,
        decoder: (BitmapFactory.Options) -> Bitmap?,
    ): Bitmap? =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            decoder(bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            decoder(
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxBytes)
                },
            )
        }.getOrNull()
}
