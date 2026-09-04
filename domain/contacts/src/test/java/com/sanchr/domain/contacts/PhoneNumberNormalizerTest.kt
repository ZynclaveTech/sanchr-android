package com.sanchr.domain.contacts

import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneNumberNormalizerTest {
    private fun n(
        raw: String,
        own: String?,
    ) = PhoneNumberNormalizer.candidates(raw, own)

    @Test
    fun `indian local formats resolve against an indian owner`() {
        val own = "+919876543210"
        assertEquals(listOf("+919876543211"), n("98765 43211", own))
        assertEquals(listOf("+919876543211"), n("098765-43211", own)) // trunk zero
        assertEquals(listOf("+919876543211"), n("91 98765 43211", own)) // cc without plus
    }

    @Test
    fun `us local formats resolve against a us owner`() {
        val own = "+14155550000"
        assertEquals(listOf("+14155551234"), n("(415) 555-1234", own))
        assertEquals(listOf("+14155551234"), n("1 415 555 1234", own))
    }

    @Test
    fun `international forms are kept and the double-zero prefix is rewritten`() {
        assertEquals(listOf("+442071234567"), n("+44 20 7123 4567", "+919876543210"))
        assertEquals(listOf("+442071234567"), n("0044 20 7123 4567", "+919876543210"))
    }

    @Test
    fun `an international number that also parses nationally yields both`() {
        // "+91…" is international; as digits "91…" also matches the cc-without-plus rule.
        assertEquals(listOf("+919876543211", "+919876543211").distinct(), n("+919876543211", "+919876543210"))
        // Different-length national: only the international reading survives.
        assertEquals(listOf("+14155551234"), n("+14155551234", "+919876543210"))
    }

    @Test
    fun `without an owner number only international forms qualify`() {
        assertEquals(listOf("+919876543211"), n("+91 98765 43211", null))
        assertEquals(emptyList(), n("98765 43211", null))
    }

    @Test
    fun `shapes that fit no rule yield no candidate, never a guess`() {
        val own = "+919876543210"
        assertEquals(emptyList(), n("112", own)) // short code
        assertEquals(emptyList(), n("", own))
        assertEquals(emptyList(), n("call me", own))
        assertEquals(emptyList(), n("+1", own)) // too short to be E.164
    }

    @Test
    fun `calling codes are split by the ITU one-two-three digit rule`() {
        assertEquals("1", PhoneNumberNormalizer.callingCodeOf("14155551234"))
        assertEquals("7", PhoneNumberNormalizer.callingCodeOf("79123456789"))
        assertEquals("44", PhoneNumberNormalizer.callingCodeOf("442071234567"))
        assertEquals("91", PhoneNumberNormalizer.callingCodeOf("919876543210"))
        assertEquals("353", PhoneNumberNormalizer.callingCodeOf("353871234567")) // Ireland: 3 digits
        assertEquals("971", PhoneNumberNormalizer.callingCodeOf("971501234567")) // UAE: 3 digits
    }
}
