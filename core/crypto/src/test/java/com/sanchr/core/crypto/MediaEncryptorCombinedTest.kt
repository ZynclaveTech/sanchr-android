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

class MediaEncryptorChunkedTest {
    private val key = ByteArray(32) { (it * 3).toByte() }

    @Test
    fun `sealAny stays single-shot up to 1 MiB and chunks above, and openAny reads both`() {
        val small = ByteArray(MediaEncryptor.CHUNK_SIZE) { it.toByte() }
        val sealedSmall = MediaEncryptor.sealAny(small, key)
        assertEquals(12 + small.size + 16, sealedSmall.size)
        assertContentEquals(small, MediaEncryptor.openAny(sealedSmall, key))

        val big = ByteArray(MediaEncryptor.CHUNK_SIZE * 2 + 12345) { (it % 251).toByte() }
        val sealedBig = MediaEncryptor.sealAny(big, key)
        // Three chunks, each carrying its own nonce and tag — the iOS layout.
        assertEquals(big.size + 3 * (12 + 16), sealedBig.size)
        assertContentEquals(big, MediaEncryptor.openAny(sealedBig, key))
        assertContentEquals(big, MediaEncryptor.openChunked(sealedBig, key))
    }

    @Test
    fun `a corrupted chunk fails closed`() {
        val big = ByteArray(MediaEncryptor.CHUNK_SIZE + 10)
        val sealed = MediaEncryptor.sealAny(big, key).also { it[it.lastIndex] = (it[it.lastIndex] + 1).toByte() }
        assertFailsWith<Exception> { MediaEncryptor.openAny(sealed, key) }
    }
}
