package com.sanchr.feature.chats.stickers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import com.sanchr.domain.messaging.media.AttachmentUploader
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Turns an emoji into the PNG that actually gets sent.
 *
 * A sticker travels as an ordinary `image/png` attachment — the same shape
 * iOS sends — so neither side needs a sticker content type and an older
 * client shows it as the picture it is rather than as an unknown message.
 */
object StickerRenderer {
    /** Matches iOS `StickerStore.renderStickerPNG`'s default. */
    const val SIZE_PX = 160

    /**
     * [emoji] drawn centred on a transparent square.
     *
     * Measured rather than positioned by font metrics: emoji glyphs vary in
     * how they sit on the baseline, and centring on the reported ascent
     * leaves some of them visibly high in the square.
     */
    fun render(
        emoji: String,
        sizePx: Int = SIZE_PX,
    ): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = sizePx * GLYPH_FRACTION
            }
        val bounds = Rect()
        paint.getTextBounds(emoji, 0, emoji.length, bounds)
        val x = (sizePx - bounds.width()) / 2f - bounds.left
        val y = (sizePx + bounds.height()) / 2f - bounds.bottom
        canvas.drawText(emoji, x, y, paint)
        return bitmap
    }

    /** [emoji] as an attachment ready for the ordinary send path. */
    fun prepare(
        emoji: String,
        sizePx: Int = SIZE_PX,
    ): AttachmentUploader.Prepared {
        val bitmap = render(emoji, sizePx)
        val bytes =
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
                out.toByteArray()
            }
        return AttachmentUploader.Prepared(
            bytes = bytes,
            mimeType = "image/png",
            fileName = "sticker-${UUID.randomUUID()}.png",
            width = bitmap.width,
            height = bitmap.height,
        )
    }

    /** How much of the square the glyph fills, as iOS. */
    private const val GLYPH_FRACTION = 0.8f

    /** Ignored for PNG, which is lossless, but the parameter is required. */
    private const val PNG_QUALITY = 100
}
