package com.sanchr.core.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CallCiphertextFramingTest {
    @Test
    fun `unframe splits the type byte from the body`() {
        val (type, body) = CallCiphertextFraming.unframe(byteArrayOf(0x02, 9, 8, 7))
        assertEquals(CallCiphertextFraming.WHISPER, type)
        assertContentEquals(byteArrayOf(9, 8, 7), body)
    }

    @Test
    fun `unknown type bytes and short inputs are refused`() {
        assertFailsWith<CallCiphertextFraming.UnknownTypeException> { CallCiphertextFraming.unframe(byteArrayOf(0x00, 1)) }
        assertFailsWith<CallCiphertextFraming.UnknownTypeException> { CallCiphertextFraming.unframe(byteArrayOf(0x03, 1)) }
        assertFailsWith<IllegalArgumentException> { CallCiphertextFraming.unframe(byteArrayOf(0x01)) }
        assertFailsWith<IllegalArgumentException> { CallCiphertextFraming.unframe(ByteArray(0)) }
    }
}
