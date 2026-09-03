package com.sanchr.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingDestinationTest {
    @Test
    fun `a conversation extra opens the chat`() {
        val d = PendingDestination.fromExtras(mapOf("conversationId" to "c-1"))
        assertEquals(PendingDestination.Conversation("c-1"), d)
        assertEquals("chats/detail/c-1", d!!.route)
    }

    @Test
    fun `a call extra opens the call with its action`() {
        val d = PendingDestination.fromExtras(mapOf("call_id" to "k-9", "call_action" to "answer"))
        assertEquals(PendingDestination.Call("k-9", "answer"), d)
        assertEquals("calls/active/k-9?action=answer", d!!.route)
    }

    @Test
    fun `nothing relevant means nothing pending`() {
        assertNull(PendingDestination.fromExtras(emptyMap()))
        assertNull(PendingDestination.fromExtras(mapOf("conversationId" to "")))
    }
}
