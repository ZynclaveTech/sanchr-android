package com.sanchr.core.crypto.profile

import com.sanchr.core.crypto.profile.ProfileCrypto.ProfileField
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * Pins the construction iOS `ProfileCrypto.swift` uses, so ciphertext made on
 * one platform opens on the other. There is no cross-platform fixture yet —
 * iOS has no known-answer test either — so the HKDF is pinned against the RFC
 * vector and the AEAD layout against its documented offsets.
 */
class ProfileCryptoTest {
    private val key = ByteArray(32) { 0xAB.toByte() }

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `hkdf matches RFC 5869 test case 1`() {
        val okm =
            ProfileCrypto.hkdfSha256(
                ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
                salt = hex("000102030405060708090a0b0c"),
                info = hex("f0f1f2f3f4f5f6f7f8f9"),
                length = 42,
            )
        assertContentEquals(
            hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
            okm,
        )
    }

    @Test
    fun `hkdf with no salt equals hkdf with HashLen zero bytes`() {
        // RFC 5869 defines the absent salt as HashLen zeros; HMAC also pads an
        // empty key to zeros, so both spellings must agree — this is what makes
        // CryptoKit's salt-less derive line up with ours.
        val a = ProfileCrypto.hkdfSha256(key, ByteArray(0), "x".toByteArray(), 32)
        val b = ProfileCrypto.hkdfSha256(key, ByteArray(32), "x".toByteArray(), 32)
        assertContentEquals(a, b)
    }

    @Test
    fun `round trips utf-8 including multi-byte text`() {
        val text = "Hello, World! 🌍"
        val blob = ProfileCrypto.encryptField(text, key, ProfileField.DISPLAY_NAME)
        assertEquals(text, ProfileCrypto.decryptField(blob, key, ProfileField.DISPLAY_NAME))
    }

    @Test
    fun `blob is nonce then ciphertext then tag, 28 bytes of overhead`() {
        val blob = ProfileCrypto.encryptField("abc", key, ProfileField.BIO)
        assertEquals(3 + ProfileCrypto.NONCE_SIZE + ProfileCrypto.TAG_SIZE, blob.size)
        // The empty display name is still encrypted on iOS: a 28-byte blob, not empty.
        assertEquals(28, ProfileCrypto.encryptField("", key, ProfileField.DISPLAY_NAME).size)
    }

    @Test
    fun `each field derives an independent subkey`() {
        // Same plaintext and key; the label alone must change the ciphertext —
        // and decrypting under the wrong field must fail, not yield the name.
        val blob = ProfileCrypto.encryptField("Alice", key, ProfileField.DISPLAY_NAME)
        assertFailsWith<ProfileCryptoException> { ProfileCrypto.decryptField(blob, key, ProfileField.BIO) }
        assertNotEquals(
            ProfileCrypto.deriveFieldKey(key, ProfileField.DISPLAY_NAME).toList(),
            ProfileCrypto.deriveFieldKey(key, ProfileField.BIO).toList(),
        )
    }

    @Test
    fun `same input twice uses a fresh nonce`() {
        val a = ProfileCrypto.encryptField("Alice", key, ProfileField.DISPLAY_NAME)
        val b = ProfileCrypto.encryptField("Alice", key, ProfileField.DISPLAY_NAME)
        assertFalse(a.contentEquals(b))
        assertFalse(a.copyOfRange(0, 12).contentEquals(b.copyOfRange(0, 12)))
    }

    @Test
    fun `tampering with the trailing tag byte is detected`() {
        val blob = ProfileCrypto.encryptField("Alice", key, ProfileField.DISPLAY_NAME)
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0xFF).toByte()
        assertFailsWith<ProfileCryptoException> { ProfileCrypto.decryptField(blob, key, ProfileField.DISPLAY_NAME) }
    }

    @Test
    fun `wrong key fails closed`() {
        val blob = ProfileCrypto.encryptField("Alice", key, ProfileField.DISPLAY_NAME)
        val other = ByteArray(32) { 0xCD.toByte() }
        assertFailsWith<ProfileCryptoException> { ProfileCrypto.decryptField(blob, other, ProfileField.DISPLAY_NAME) }
    }

    @Test
    fun `short keys and short blobs are rejected`() {
        assertFailsWith<ProfileCryptoException> { ProfileCrypto.encryptField("x", ByteArray(16), ProfileField.BIO) }
        assertFailsWith<ProfileCryptoException> { ProfileCrypto.decryptField(ByteArray(27), key, ProfileField.BIO) }
    }

    @Test
    fun `version is the first 16 bytes of sha-256 of the key`() {
        val expected = MessageDigest.getInstance("SHA-256").digest(key).copyOf(16)
        assertContentEquals(expected, ProfileCrypto.version(key))
        assertEquals(16, ProfileCrypto.version(key).size)
    }

    @Test
    fun `generated keys are 32 bytes and distinct`() {
        val a = ProfileCrypto.generateKey()
        val b = ProfileCrypto.generateKey()
        assertEquals(32, a.size)
        assertFalse(a.contentEquals(b))
    }
}
