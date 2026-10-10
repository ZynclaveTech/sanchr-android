package com.sanchr.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Resolving a stored dial code back to a country.
 *
 * `+1` is both the United States and Canada. The previous lookup used
 * `singleOrNull`, so picking either of them resolved to null, the screen fell
 * back to the SIM country, and the chip then showed a different country from
 * the one the state held — and from the dial code actually submitted.
 */
class CountryLookupTest {
    @Test
    fun `a shared dial code resolves instead of returning null`() {
        val resolved = findCountryByDialCode("+1")

        assertNotNull(resolved, "+1 resolved to nothing, so the chip could not show the choice")
        assertEquals("+1", resolved.dialCode)
    }

    @Test
    fun `an iso picks between countries that share a dial code`() {
        assertEquals("CA", findCountryByDialCode("+1", iso = "CA")?.iso)
        assertEquals("US", findCountryByDialCode("+1", iso = "US")?.iso)
    }

    @Test
    fun `an unambiguous dial code still resolves, with or without an iso`() {
        assertEquals("IN", findCountryByDialCode("+91")?.iso)
        assertEquals("IN", findCountryByDialCode("+91", iso = "IN")?.iso)
    }

    @Test
    fun `an iso that does not match the dial code falls back to the dial code`() {
        // Defensive: state carrying a stale iso must not lose the dial code.
        assertEquals("+91", findCountryByDialCode("+91", iso = "US")?.dialCode)
    }

    @Test
    fun `a dial code we do not carry resolves to nothing`() {
        assertNull(findCountryByDialCode("+99999"))
    }
}
