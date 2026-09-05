package com.sanchr.core.designsystem.component

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The safety-number QR carries raw fingerprint bytes, not text. If encoding
 * or decoding ever routed them through UTF-8 every byte above 0x7F would be
 * replaced, the codes would stop matching iOS, and two people comparing them
 * would be told they disagree when they do not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class QrBinaryRoundTripTest {
    @Test
    fun `high bytes survive the encode and decode unchanged`() {
        val payload = ByteArray(64) { (it * 5 + 0x80).toByte() }

        val decoded = roundTrip(payload)

        assertContentEquals(payload, decoded)
    }

    @Test
    fun `every possible byte value round-trips`() {
        val payload = ByteArray(256) { it.toByte() }

        assertContentEquals(payload, roundTrip(payload))
    }

    @Test
    fun `empty input encodes nothing rather than an empty symbol`() {
        assertNull(QrCodes.binaryBitmap(ByteArray(0), 256))
        assertNull(QrCodes.binaryBitmap(ByteArray(4), 0))
    }

    private fun roundTrip(payload: ByteArray): ByteArray {
        val size = 512
        val bitmap = assertNotNull(QrCodes.binaryBitmap(payload, size), "encoder returned no bitmap")
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels))))
        return QrCodes.payloadOf(result).bytes
    }
}
