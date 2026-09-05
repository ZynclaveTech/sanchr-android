package com.sanchr.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ContactDisplayNameTest {
    @Test
    fun `address book name wins over everything`() {
        assertEquals("Mum", ContactDisplayName.resolve("Mum", "+15550001", "Alice"))
    }

    @Test
    fun `phone number beats the self-asserted profile name`() {
        assertEquals("+15550001", ContactDisplayName.resolve(null, "+15550001", "Alice"))
    }

    @Test
    fun `profile name is shown with a tilde when nothing else is known`() {
        assertEquals("~Alice", ContactDisplayName.resolve(null, "", "Alice"))
    }

    @Test
    fun `the server placeholder is never shown`() {
        assertEquals("~Alice", ContactDisplayName.resolve("Sanchr User", null, "Alice"))
        assertEquals("Unknown contact", ContactDisplayName.resolve("Sanchr User", null, null))
    }

    @Test
    fun `blank inputs fall through`() {
        assertEquals("Unknown contact", ContactDisplayName.resolve("  ", " ", "  "))
    }
}
