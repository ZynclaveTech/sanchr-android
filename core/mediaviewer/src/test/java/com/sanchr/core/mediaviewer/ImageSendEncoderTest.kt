package com.sanchr.core.mediaviewer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Re-encoding a picked image before it is uploaded.
 *
 * `NATIVE` graphics: the point of this encoder is what the bytes it produces
 * contain, and Robolectric's default stub rasteriser produces none.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageSendEncoderTest {
    private fun pngBytes(
        width: Int,
        height: Int,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF336699.toInt())
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun resolverFor(bytes: ByteArray): ContentResolver =
        mockk<ContentResolver> {
            // Re-opened per call: the bounds pass consumes the header.
            every { openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        }

    private val uri: Uri = mockk(relaxed = true)

    @Test
    fun `a picked png comes back as a jpeg of the same size`() {
        val encoded = ImageSendEncoder.encode(resolverFor(pngBytes(640, 480)), uri)

        assertNotNull(encoded)
        assertEquals(ImageSendEncoder.JPEG_MIME, encoded.mimeType)
        assertEquals(640, encoded.width)
        assertEquals(480, encoded.height)
        val decoded = BitmapFactory.decodeByteArray(encoded.bytes, 0, encoded.bytes.size)
        assertNotNull(decoded, "the re-encoded bytes are not a decodable image")
        assertEquals(640, decoded.width)
    }

    @Test
    fun `an image longer than the cap is scaled down, keeping its aspect ratio`() {
        val encoded = ImageSendEncoder.encode(resolverFor(pngBytes(800, 400)), uri, maxLongEdge = 200)

        assertNotNull(encoded)
        assertEquals(200, encoded.width)
        assertEquals(100, encoded.height)
    }

    @Test
    fun `an image already within the cap is not scaled`() {
        val encoded = ImageSendEncoder.encode(resolverFor(pngBytes(120, 90)), uri, maxLongEdge = 1024)

        assertNotNull(encoded)
        assertEquals(120, encoded.width)
        assertEquals(90, encoded.height)
    }

    @Test
    fun `an unreadable image returns null so the caller can fall back`() {
        val resolver = mockk<ContentResolver> { every { openInputStream(any()) } returns null }

        assertEquals(null, ImageSendEncoder.encode(resolver, uri))
    }

    @Test
    fun `gif is left alone, because re-encoding one keeps a single frame`() {
        assertFalse(ImageSendEncoder.handles("image/gif"))
        assertTrue(ImageSendEncoder.handles("image/jpeg"))
        assertTrue(ImageSendEncoder.handles("image/heic"))
        assertFalse(ImageSendEncoder.handles("video/mp4"))
        assertFalse(ImageSendEncoder.handles("application/pdf"))
    }

    @Test
    fun `the output carries no EXIF, because it is written from pixels`() {
        val encoded = assertNotNull(ImageSendEncoder.encode(resolverFor(pngBytes(64, 64)), uri))

        // APP1/Exif is the marker a GPS tag would live under. A JPEG produced
        // by Bitmap.compress has no APP1 segment at all; asserting its absence
        // is what keeps a future "just forward the original" change honest.
        val marker = byteArrayOf(0xFF.toByte(), 0xE1.toByte())
        val hasApp1 =
            (0 until encoded.bytes.size - 1).any { i ->
                encoded.bytes[i] == marker[0] && encoded.bytes[i + 1] == marker[1]
            }
        assertFalse(hasApp1, "the re-encoded JPEG still carries an APP1 (EXIF) segment")
    }
}
