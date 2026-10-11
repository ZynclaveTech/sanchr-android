package com.sanchr.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sign-in offered 31 countries; anyone elsewhere could not register. */
class CountriesTest {
    private fun country(iso: String): Country = assertNotNull(SanchrCountries.firstOrNull { it.iso == iso }, "$iso is missing")

    @Test
    fun `the list is every country, not a short one`() {
        assertTrue(SanchrCountries.size > 200, "only ${SanchrCountries.size}")
        assertEquals(SanchrCountries.size, SanchrCountries.map { it.iso }.toSet().size, "each region once")
    }

    @Test
    fun `countries the short list left out are there`() {
        listOf("NP" to "+977", "LK" to "+94", "TH" to "+66", "MY" to "+60", "GH" to "+233", "PL" to "+48").forEach { (iso, code) ->
            assertEquals(code, country(iso).dialCode, iso)
        }
    }

    @Test
    fun `every entry has a name, a flag and a well-formed calling code`() {
        SanchrCountries.forEach { c ->
            assertTrue(c.name.isNotBlank(), c.iso)
            assertTrue(Regex("^\\+\\d{1,3}$").matches(c.dialCode), "${c.iso} ${c.dialCode}")
            assertEquals(2, c.flagEmoji.codePointCount(0, c.flagEmoji.length), c.iso)
            assertTrue(c.nationalLength.first in 4..c.nationalLength.last && c.nationalLength.last <= 14, c.iso)
        }
    }

    @Test
    fun `they are in name order, which is how the picker shows them`() {
        assertEquals(SanchrCountries.map { it.name }.sorted(), SanchrCountries.map { it.name })
    }

    @Test
    fun `what people type becomes the number the server knows`() {
        assertEquals("+14155551234", country("US").e164("(415) 555-1234"))
        assertEquals("+919876543210", country("IN").e164("98765 43210"))
    }

    /** 07700 900123 used to be sent as +4407700900123. */
    @Test
    fun `the zero dialled at home is not part of the number`() {
        assertEquals("+447700900123", country("GB").e164("07700 900123"))
        assertEquals("+919876543210", country("IN").e164("09876543210"))
    }

    @Test
    fun `a number the country could not have is refused`() {
        assertNull(country("US").e164("555"))
        assertNull(country("US").e164("41555512345678"))
        assertNull(country("IN").e164(""))
        assertNull(country("IN").e164("abc"))
    }

    /** +1 is two countries; the region tells them apart. */
    @Test
    fun `a shared calling code is resolved by region when it is known`() {
        assertEquals("CA", findCountryByDialCode("+1", "CA")?.iso)
        assertEquals("US", findCountryByDialCode("+1", "US")?.iso)
        assertNotNull(findCountryByDialCode("+1"))
        assertNull(findCountryByDialCode("+0"))
    }

    @Test
    fun `a flag is the two regional indicators for its region`() {
        assertEquals("🇮🇳", flagOf("IN"))
        assertEquals("🇺🇸", flagOf("us"))
    }
}
