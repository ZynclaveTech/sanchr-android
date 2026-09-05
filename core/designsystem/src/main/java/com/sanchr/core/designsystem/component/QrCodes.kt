package com.sanchr.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.Result
import com.google.zxing.ResultMetadataType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * What one scanned QR code carried.
 *
 * Keeps both readings because callers need different ones: a link is text, a
 * Signal fingerprint is bytes, and converting between them after the fact
 * loses information for anything above ASCII.
 */
data class QrPayload(
    val text: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QrPayload) return false
        return text == other.text && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * text.hashCode() + bytes.contentHashCode()
}

/** Renders a string as a QR bitmap, matching iOS `QRCodeGenerator`'s settings. */
object QrCodes {
    /**
     * The QR for [content] at [sizePx] square, or null when it cannot be
     * encoded. Error correction is H (about 30% redundancy) as on iOS, which
     * keeps the code scannable with a mark drawn over its centre.
     */
    fun bitmap(
        content: String,
        sizePx: Int,
    ): Bitmap? {
        if (content.isEmpty() || sizePx <= 0) return null
        return runCatching {
            val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, EncodeHintType.MARGIN to QUIET_ZONE_MODULES)
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val pixels = IntArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                val row = y * sizePx
                for (x in 0 until sizePx) {
                    pixels[row + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    /**
     * The QR for arbitrary [bytes], as the Signal scannable fingerprint needs.
     *
     * Encoded as ISO-8859-1 so ZXing picks byte mode and writes the bytes
     * through unchanged, which is the same symbol iOS's `CIQRCodeGenerator`
     * produces from raw `Data`. Anything that round-trips through UTF-8 would
     * mangle every byte above 0x7F, and a fingerprint is full of them.
     */
    fun binaryBitmap(
        bytes: ByteArray,
        sizePx: Int,
    ): Bitmap? {
        if (bytes.isEmpty() || sizePx <= 0) return null
        return runCatching {
            val hints =
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                    EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
                    EncodeHintType.CHARACTER_SET to Charsets.ISO_8859_1.name(),
                )
            val matrix = QRCodeWriter().encode(String(bytes, Charsets.ISO_8859_1), BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val pixels = IntArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                val row = y * sizePx
                for (x in 0 until sizePx) {
                    pixels[row + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    /**
     * The raw bytes behind a decoded QR result.
     *
     * Prefers ZXing's byte segments, which are the payload before any text
     * decoding, and falls back to re-encoding the text as ISO-8859-1 — the
     * same two-tier read iOS does, so a code written by either app is
     * recovered byte for byte.
     */
    internal fun payloadOf(result: Result): QrPayload {
        @Suppress("UNCHECKED_CAST")
        val segments = result.resultMetadata?.get(ResultMetadataType.BYTE_SEGMENTS) as? List<ByteArray>
        val joined = segments?.takeIf { it.isNotEmpty() }?.reduce { a, b -> a + b }
        val text = result.text.orEmpty()
        return QrPayload(text = text, bytes = joined ?: text.toByteArray(Charsets.ISO_8859_1))
    }

    /** The link a profile's QR carries; iOS encodes exactly this, so either app can read the other's. */
    fun profileLink(userId: String): String = "https://sanchr.com/u/$userId"

    /** Modules of quiet zone around the code; the spec's minimum is 4. */
    private const val QUIET_ZONE_MODULES = 1
}
