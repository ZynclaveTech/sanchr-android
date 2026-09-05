package com.sanchr.feature.chats.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.sanchr.core.model.BlurHash

/** Bitmap adapters over [BlurHash] for outgoing encode and placeholder decode. */
object BlurHashImages {
    private const val ENCODE_MAX_SIDE = 100
    private const val PLACEHOLDER_SIDE = 32
    private const val CACHE_ENTRIES = 64
    private val placeholders = LruCache<String, Bitmap>(CACHE_ENTRIES)

    /**
     * The BlurHash of an encoded image, computed from a ≤100 px downsample
     * as iOS does; null when the bytes do not decode as an image.
     */
    fun encode(imageBytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ENCODE_MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, opts) ?: return null
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            BlurHash.encode(pixels, bitmap.width, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    /** A small placeholder bitmap for [hash] (cached), or null when the hash is invalid. */
    fun placeholder(hash: String): Bitmap? {
        placeholders.get(hash)?.let { return it }
        val pixels = BlurHash.decode(hash, PLACEHOLDER_SIDE, PLACEHOLDER_SIDE) ?: return null
        val bitmap = Bitmap.createBitmap(pixels, PLACEHOLDER_SIDE, PLACEHOLDER_SIDE, Bitmap.Config.ARGB_8888)
        placeholders.put(hash, bitmap)
        return bitmap
    }
}
