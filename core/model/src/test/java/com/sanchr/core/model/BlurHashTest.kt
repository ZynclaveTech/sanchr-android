package com.sanchr.core.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlurHashTest {
    // The reference basis (cos(pi*i*x/w), no half-pixel offset) leaks about
    // 2/w of the DC into each AC term, which is why encoders (iOS included)
    // hash a ~100 px downsample rather than a thumbnail.
    private fun solid(
        argb: Int,
        w: Int = 96,
        h: Int = 72,
    ) = IntArray(w * h) { argb }

    @Test
    fun `4x3 components pack into the iOS 28-character string`() {
        val hash = BlurHash.encode(solid(0xFF3366CC.toInt()), 96, 72)
        assertEquals(28, hash.length)
        // Size flag for 4x3 is (4-1) + (3-1)*9 = 21 -> 'L', exactly as iOS's 4x3 hashes start.
        assertEquals('L', hash[0])
    }

    @Test
    fun `a solid colour survives an encode-decode round trip`() {
        val hash = BlurHash.encode(solid(0xFF3366CC.toInt()), 96, 72)
        val pixels = assertNotNull(BlurHash.decode(hash, 4, 4))
        pixels.forEach { p ->
            assertTrue(abs(((p shr 16) and 0xFF) - 0x33) <= 8, "red ${Integer.toHexString(p)}")
            assertTrue(abs(((p shr 8) and 0xFF) - 0x66) <= 8, "green ${Integer.toHexString(p)}")
            assertTrue(abs((p and 0xFF) - 0xCC) <= 8, "blue ${Integer.toHexString(p)}")
            assertEquals(0xFF, (p ushr 24), "opaque")
        }
    }

    @Test
    fun `a left-right gradient keeps its orientation`() {
        val w = 96
        val h = 48
        val pixels = IntArray(w * h) { i -> if (i % w < w / 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val out = assertNotNull(BlurHash.decode(BlurHash.encode(pixels, w, h), 16, 4))
        val left = (0 until 8).map { out[it] and 0xFF }.average()
        val right = (8 until 16).map { out[it] and 0xFF }.average()
        assertTrue(right > left + 80, "left=$left right=$right")
    }

    @Test
    fun `a well-known public hash decodes and garbage does not`() {
        assertNotNull(BlurHash.decode("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 32, 32))
        assertNull(BlurHash.decode("LEHV6nWB2yk8pyo0adR*.7kCMdn", 32, 32))
        assertNull(BlurHash.decode("L\"HV6nWB2yk8pyo0adR*.7kCMdnj", 32, 32))
        assertNull(BlurHash.decode("", 32, 32))
        assertNull(BlurHash.decode("LEHV6n", 0, 32))
    }
}
