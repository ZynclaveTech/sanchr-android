package com.sanchr.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

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

    /** The link a profile's QR carries; iOS encodes exactly this, so either app can read the other's. */
    fun profileLink(userId: String): String = "https://sanchr.com/u/$userId"

    /** Modules of quiet zone around the code; the spec's minimum is 4. */
    private const val QUIET_ZONE_MODULES = 1
}
