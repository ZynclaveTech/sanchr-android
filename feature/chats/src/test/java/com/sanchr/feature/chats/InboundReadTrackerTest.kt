package com.sanchr.feature.chats

import kotlin.test.Test
import kotlin.test.assertEquals

class InboundReadTrackerTest {
    private val read = mutableListOf<String>()
    private val tracker = InboundReadTracker(read::add)

    @Test
    fun `the first message it sees is left to opening the chat`() {
        tracker.onVisibilityChanged(true)

        tracker.onNewestInbound("m1")

        assertEquals(emptyList(), read)
    }

    @Test
    fun `a message arriving in front is read at once`() {
        tracker.onVisibilityChanged(true)
        tracker.onNewestInbound("m1")

        tracker.onNewestInbound("m2")

        assertEquals(listOf("m2"), read)
    }

    @Test
    fun `the same newest message again is not read twice`() {
        tracker.onVisibilityChanged(true)
        tracker.onNewestInbound("m1")
        tracker.onNewestInbound("m2")

        tracker.onNewestInbound("m2")
        tracker.onNewestInbound(null)

        assertEquals(listOf("m2"), read)
    }

    @Test
    fun `nothing is read while hidden, and the newest is read on coming back`() {
        tracker.onVisibilityChanged(true)
        tracker.onNewestInbound("m1")
        tracker.onVisibilityChanged(false)

        tracker.onNewestInbound("m2")
        tracker.onNewestInbound("m3")
        assertEquals(emptyList(), read)

        tracker.onVisibilityChanged(true)
        assertEquals(listOf("m3"), read)
    }

    @Test
    fun `coming back with nothing new reads nothing`() {
        tracker.onVisibilityChanged(true)
        tracker.onNewestInbound("m1")

        tracker.onVisibilityChanged(false)
        tracker.onVisibilityChanged(true)
        tracker.onVisibilityChanged(true)

        assertEquals(emptyList(), read)
    }

    /** The stream can deliver before the screen first reports itself in front. */
    @Test
    fun `a message that arrives before the screen has ever been in front waits for it`() {
        tracker.onNewestInbound("m1")
        tracker.onNewestInbound("m2")
        assertEquals(emptyList(), read)

        tracker.onVisibilityChanged(true)

        assertEquals(listOf("m2"), read)
    }
}
