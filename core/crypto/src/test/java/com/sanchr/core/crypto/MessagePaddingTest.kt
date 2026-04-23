package com.sanchr.core.crypto

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Test

class MessagePaddingTest {
    @Test
    fun `pad then strip round-trips empty input`() {
        val padded = MessagePadding.pad(ByteArray(0))
        assertEquals(160, padded.size)
        assertEquals(0x80.toByte(), padded[0])
        val stripped = MessagePadding.strip(padded)
        assertTrue(stripped.isEmpty())
    }

    @Test
    fun `pad then strip round-trips non-empty input`() {
        val input = "hello".toByteArray(Charsets.UTF_8)
        val padded = MessagePadding.pad(input)
        assertEquals(160, padded.size)
        val stripped = MessagePadding.strip(padded)
        assertTrue(input.contentEquals(stripped), "round-tripped bytes must match")
    }

    @Test
    fun `strip throws when no 0x80 sentinel present`() {
        val bogus = ByteArray(160) // all zeros, no sentinel
        assertFailsWith<IllegalArgumentException> { MessagePadding.strip(bogus) }
    }

    @Test
    fun `pad rolls to next multiple when data already fills a block`() {
        val minSize = 16
        // Exactly one block of data → result must be TWO blocks so the 0x80
        // sentinel has somewhere to live.
        val input = ByteArray(minSize) { 0x11 }
        val padded = MessagePadding.pad(input, minSize)
        assertEquals(2 * minSize, padded.size)
        assertEquals(0x80.toByte(), padded[minSize])

        // And a 1-byte-short input pads up to exactly one block.
        val short = ByteArray(minSize - 1) { 0x22 }
        val paddedShort = MessagePadding.pad(short, minSize)
        assertEquals(minSize, paddedShort.size)
        assertEquals(0x80.toByte(), paddedShort[minSize - 1])

        val stripped = MessagePadding.strip(padded)
        assertTrue(input.contentEquals(stripped))
    }
}
