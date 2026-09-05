package com.sanchr.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The contact card's body is a bare `{"name","phoneNumber"}` object, not a
 * `MessageContent` envelope — iOS `MessageSender` encodes exactly that and
 * `MessageRepository` parses those two keys. Pinned because nothing shared
 * declares the shape.
 */
class ContactCardWireTest {
    @Test
    fun `the encoded body is the two keys iOS reads, in that order`() {
        val encoded = ContactCard(name = "Ada Lovelace", phoneNumber = "+15550100").encode()

        assertEquals("""{"name":"Ada Lovelace","phoneNumber":"+15550100"}""", encoded)
    }

    @Test
    fun `what iOS sends decodes back, including a card carrying only a number`() {
        assertEquals(
            ContactCard(name = "Ada", phoneNumber = "+15550100"),
            ContactCard.decode("""{"name":"Ada","phoneNumber":"+15550100"}"""),
        )
        assertEquals(
            ContactCard(name = "", phoneNumber = "+15550100"),
            ContactCard.decode("""{"name":"","phoneNumber":"+15550100"}"""),
        )
    }
}
