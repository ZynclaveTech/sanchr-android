package com.sanchr.core.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class VaultKeyDerivationTest {
    private val dls = ByteArray(32) { it.toByte() }
    private val salt = ByteArray(32) { 0xAA.toByte() }
    private val id = "0f2c1b7a-1c3e-4d5f-8a9b-0c1d2e3f4a5b"

    @Test
    fun `matches an independent HKDF-SHA256 computation with the iOS label`() {
        // HKDF(ikm=dls, salt, info="sanchr-vault-manual-v1-<id>", 32) computed with Python hmac/hashlib.
        val expected = "cc2afdca3274a987cf0a20a04302d860442281c0cc01b53e695c587382202c06"
        assertEquals(expected, VaultKeyDerivation.deriveManual(dls, salt, id).joinToString("") { "%02x".format(it) })
    }

    @Test
    fun `salt and item id both separate keys`() {
        val a = VaultKeyDerivation.deriveManual(dls, salt, id)
        assertFalse(a.contentEquals(VaultKeyDerivation.deriveManual(dls, ByteArray(32) { 0xBB.toByte() }, id)))
        assertFalse(a.contentEquals(VaultKeyDerivation.deriveManual(dls, salt, "other-id")))
        assertContentEquals(a, VaultKeyDerivation.deriveManual(dls, salt, id))
    }

    @Test
    fun `a wrong-sized salt is refused`() {
        assertFailsWith<IllegalArgumentException> { VaultKeyDerivation.deriveManual(dls, ByteArray(16), id) }
    }

    @Test
    fun `Hkdf reproduces RFC 5869 test case 1`() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val okm = Hkdf.sha256(ikm, salt, info, 42).joinToString("") { "%02x".format(it) }
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865", okm)
    }
}
