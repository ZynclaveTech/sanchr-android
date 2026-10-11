package com.sanchr.core.notifications

import android.app.NotificationManager
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VisibleConversationTest {
    private val context =
        mockk<Context>(relaxed = true) {
            every { getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<NotificationManager>(relaxed = true)
        }
    private val handler = NotificationHandler(context)

    @Test
    fun `no chat is in front until one says it is`() {
        assertNull(handler.visibleConversationId)
    }

    @Test
    fun `a chat coming to the front is the visible one, and leaving clears it`() {
        handler.setConversationVisible("a", visible = true)
        assertEquals("a", handler.visibleConversationId)

        handler.setConversationVisible("a", visible = false)
        assertNull(handler.visibleConversationId)
    }

    /** Opening B from A: B reports itself before A has finished leaving. */
    @Test
    fun `a chat that leaves late does not clear the one that replaced it`() {
        handler.setConversationVisible("a", visible = true)
        handler.setConversationVisible("b", visible = true)

        handler.setConversationVisible("a", visible = false)

        assertEquals("b", handler.visibleConversationId)
    }
}
