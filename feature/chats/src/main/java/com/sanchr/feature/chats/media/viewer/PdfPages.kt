package com.sanchr.feature.chats.media.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    private val mutex = Mutex()

    val pageCount: Int get() = renderer.pageCount

    /**
     * Page [index] rendered [widthPx] wide, its aspect ratio preserved.
     *
     * Drawn onto white first: PDF pages are transparent where nothing is
     * printed, and a transparent bitmap on a dark background renders as black
     * text on black.
     *
     * The height comes from the page's own dimensions, and those come from a
     * file a stranger sent. A page declaring a 1:10000 ratio would ask for a
     * bitmap of hundreds of megabytes and take the process down, so the
     * height is clamped and the allocation checked against a budget before it
     * is made. Null for anything outside that, which the caller already
     * treats as "cannot be shown here".
     *
     * Serialised on a mutex: PdfRenderer allows one open page at a time and
     * throws on a second, and pages are rendered as they scroll into view, so
     * two can otherwise be in flight at once.
     */
    suspend fun render(
        index: Int,
        widthPx: Int,
    ): Bitmap? {
        if (index !in 0 until pageCount || widthPx <= 0 || widthPx > MAX_DIMENSION_PX) return null
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    renderer.openPage(index).use { page ->
                        if (page.width <= 0 || page.height <= 0) {
                            return@use null
                        }
                        val scaled = (widthPx.toLong() * page.height / page.width).coerceAtLeast(1L)
                        val height = scaled.coerceAtMost(MAX_DIMENSION_PX.toLong()).toInt()
                        if (widthPx.toLong() * height * BYTES_PER_PIXEL > MAX_PAGE_BYTES) {
                            return@use null
                        }
                        val bitmap = createBitmap(widthPx, height)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }.getOrNull()
            }
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        /** Neither axis may exceed this, whatever the page claims. */
        private const val MAX_DIMENSION_PX = 8192

        private const val BYTES_PER_PIXEL = 4

        /** Ceiling for a single page's bitmap, around a 4000x4000 page. */
        private const val MAX_PAGE_BYTES = 64L * 1024 * 1024

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
