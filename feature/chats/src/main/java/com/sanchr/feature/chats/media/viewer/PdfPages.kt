package com.sanchr.feature.chats.media.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import java.io.Closeable
import java.io.File

/**
 * Renders a PDF's pages to bitmaps, in process.
 *
 * The framework renderer is the whole reason a PDF need not leave the app:
 * without it the only way to read an attachment is to hand the decrypted file
 * to another app along with a read grant.
 *
 * Not thread-safe, and deliberately so — `PdfRenderer` allows only one page
 * open at a time and throws if a second is opened before the first is closed.
 * Confine it to a single dispatcher.
 */
class PdfPages private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {
    val pageCount: Int get() = renderer.pageCount

    /**
     * Page [index] rendered [widthPx] wide, its aspect ratio preserved.
     *
     * Drawn onto white first: PDF pages are transparent where nothing is
     * printed, and a transparent bitmap on a dark background renders as black
     * text on black.
     */
    fun render(
        index: Int,
        widthPx: Int,
    ): Bitmap? {
        if (index !in 0 until pageCount || widthPx <= 0) return null
        return runCatching {
            renderer.openPage(index).use { page ->
                val height = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bitmap = createBitmap(widthPx, height)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }.getOrNull()
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        /**
         * A renderer for [file], or null when it is not a PDF this device can
         * open — an encrypted or damaged file among them.
         *
         * Null rather than an exception because the caller's answer is the
         * same either way: offer to open it elsewhere.
         */
        fun open(file: File): PdfPages? =
            runCatching {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    PdfPages(descriptor, PdfRenderer(descriptor))
                } catch (t: Throwable) {
                    descriptor.close()
                    throw t
                }
            }.getOrNull()
    }
}
