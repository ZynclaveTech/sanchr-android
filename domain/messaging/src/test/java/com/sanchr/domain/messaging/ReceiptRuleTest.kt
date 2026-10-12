package com.sanchr.domain.messaging

import com.sanchr.core.model.MessageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A receipt reports delivered or read, and only ever moves a message forward. */
class ReceiptRuleTest {
    private fun applies(
        receipt: MessageStatus,
        to: MessageStatus,
    ) = ReceiptRule.advancesFrom(receipt)?.contains(to) == true

    @Test
    fun `delivered applies to a message not yet delivered`() {
        assertTrue(applies(MessageStatus.DELIVERED, to = MessageStatus.SENT))
        assertTrue(applies(MessageStatus.DELIVERED, to = MessageStatus.SENDING))
        // Sent, but this phone never heard so: the receipt is the first news of it.
        assertTrue(applies(MessageStatus.DELIVERED, to = MessageStatus.FAILED))
        assertTrue(applies(MessageStatus.DELIVERED, to = MessageStatus.QUEUED))
    }

    @Test
    fun `read applies to anything short of read`() {
        assertTrue(applies(MessageStatus.READ, to = MessageStatus.SENT))
        assertTrue(applies(MessageStatus.READ, to = MessageStatus.DELIVERED))
        assertTrue(applies(MessageStatus.READ, to = MessageStatus.FAILED))
    }

    @Test
    fun `a receipt never moves a message backwards`() {
        assertFalse(applies(MessageStatus.DELIVERED, to = MessageStatus.READ))
        assertFalse(applies(MessageStatus.DELIVERED, to = MessageStatus.DELIVERED))
        assertFalse(applies(MessageStatus.READ, to = MessageStatus.READ))
    }

    /** `QUEUED` is the outbox's cue to send a message again. */
    @Test
    fun `nothing but delivered and read is a receipt`() {
        assertNull(ReceiptRule.advancesFrom(MessageStatus.QUEUED))
        assertNull(ReceiptRule.advancesFrom(MessageStatus.SENDING))
        assertNull(ReceiptRule.advancesFrom(MessageStatus.SENT))
        assertNull(ReceiptRule.advancesFrom(MessageStatus.FAILED))
        assertEquals(
            setOf(MessageStatus.DELIVERED, MessageStatus.READ),
            MessageStatus.entries
                .filter {
                    ReceiptRule.advancesFrom(it) != null
                }.toSet(),
        )
    }
}
