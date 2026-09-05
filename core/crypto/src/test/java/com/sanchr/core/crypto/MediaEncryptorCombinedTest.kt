package com.sanchr.core.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediaEncryptorCombinedTest {
    private val key = ByteArray(32) { (it * 7).toByte() }

    @Test
    fun `combined is nonce, ciphertext, tag and round-trips`() {
        val plaintext = "vault metadata".toByteArray()
        val combined = MediaEncryptor.sealCombined(plaintext, key)
        assertEquals(12 + plaintext.size + 16, combined.size)
        assertContentEquals(plaintext, MediaEncryptor.openCombined(combined, key))
    }

    @Test
    fun `a flipped byte or the wrong key fails authentication`() {
        val combined = MediaEncryptor.sealCombined("x".toByteArray(), key)
        val tampered = combined.copyOf().also { it[it.lastIndex] = (it[it.lastIndex] + 1).toByte() }
        assertFailsWith<Exception> { MediaEncryptor.openCombined(tampered, key) }
        assertFailsWith<Exception> { MediaEncryptor.openCombined(combined, ByteArray(32) { 1 }) }
        assertFailsWith<IllegalArgumentException> { MediaEncryptor.openCombined(ByteArray(10), key) }
    }
}
