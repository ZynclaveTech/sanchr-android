package com.sanchr.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactCardTest {
    @Test
    fun `round-trips the iOS wire shape`() {
        val card = ContactCard(name = "Ada Lovelace", phoneNumber = "+44 20 7946 0000")
        assertEquals("""{"name":"Ada Lovelace","phoneNumber":"+44 20 7946 0000"}""", card.encode())
        assertEquals(card, ContactCard.decode(card.encode()))
        assertEquals(card, ContactCard.decode("""{"phoneNumber":"+44 20 7946 0000","name":"Ada Lovelace","extra":1}"""))
    }

    @Test
    fun `rejects garbage and empty cards`() {
        assertNull(ContactCard.decode("not json"))
        assertNull(ContactCard.decode("""{"name":"","phoneNumber":" "}"""))
        assertNull(ContactCard.decode("""{"name":"x"}"""))
    }
}
