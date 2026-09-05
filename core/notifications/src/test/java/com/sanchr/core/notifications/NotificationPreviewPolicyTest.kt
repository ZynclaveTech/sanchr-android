package com.sanchr.core.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationPreviewPolicyTest {
    private val body = "see you at six"

    @Test
    fun `always shows the sender and the message`() {
        assertEquals(
            NotificationPreviewPolicy.Content("Ravi Kumar", body),
            NotificationPreviewPolicy.decide("always", "Ravi Kumar", body),
        )
    }

    @Test
    fun `always still shows the message when the sender is unknown`() {
        assertEquals(
            NotificationPreviewPolicy.Content("New message", body),
            NotificationPreviewPolicy.decide("always", null, body),
        )
    }

    @Test
    fun `contacts only shows a saved contact`() {
        assertEquals(
            NotificationPreviewPolicy.Content("Ravi Kumar", body),
            NotificationPreviewPolicy.decide("contacts", "Ravi Kumar", body),
        )
    }

    @Test
    fun `contacts only withholds everything for someone not in the address book`() {
        val content = NotificationPreviewPolicy.decide("contacts", null, body)

        assertEquals("New message", content.title)
        assertEquals("Open Sanchr to read it", content.body)
    }

    @Test
    fun `a blank stored name counts as unknown`() {
        assertEquals("New message", NotificationPreviewPolicy.decide("contacts", "   ", body).title)
    }

    @Test
    fun `never withholds the name and the message even for a contact`() {
        val content = NotificationPreviewPolicy.decide("never", "Ravi Kumar", body)

        assertEquals("New message", content.title)
        assertEquals("Open Sanchr to read it", content.body)
    }

    @Test
    fun `an unrecognised setting is treated as the strictest option`() {
        val content = NotificationPreviewPolicy.decide("something-newer", "Ravi Kumar", body)

        assertEquals("New message", content.title)
        assertEquals("Open Sanchr to read it", content.body)
    }
}
