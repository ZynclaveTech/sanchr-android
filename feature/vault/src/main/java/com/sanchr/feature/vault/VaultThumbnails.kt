package com.sanchr.feature.vault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * A small JPEG preview for the vault grid, generated on the device before
 * encryption so it can live inside the encrypted metadata envelope. Kept
 * under 48 KiB (the iOS cap) so the envelope stays inside the server's
 * 64 KiB limit with base64 overhead.
 */
object VaultThumbnails {
    const val MAX_BYTES = 48 * 1024
    private const val TARGET_EDGE = 320
    private const val START_QUALITY = 80
    private const val MIN_QUALITY = 30
    private const val QUALITY_STEP = 10

    fun forImage(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_EDGE && bounds.outHeight / (sample * 2) >= TARGET_EDGE) sample *= 2
        val bitmap =
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
        return try {
            compressUnderCap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun compressUnderCap(bitmap: Bitmap): ByteArray? {
        var quality = START_QUALITY
        while (quality >= MIN_QUALITY) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= MAX_BYTES) return out.toByteArray()
            quality -= QUALITY_STEP
        }
        return null
    }
}
