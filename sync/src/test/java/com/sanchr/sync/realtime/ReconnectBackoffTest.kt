package com.sanchr.sync.realtime

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReconnectBackoffTest {
    /** The middle of the spread, so the wait is exactly the base. */
    private val noJitter =
        object : Random() {
            override fun nextBits(bitCount: Int): Int = 0

            override fun nextLong(until: Long): Long = until / 2
        }

    @Test
    fun `the wait doubles from a second and stops at half a minute`() {
        val backoff = ReconnectBackoff(noJitter)

        val waits = List(8) { backoff.next() }

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L), waits)
    }

    @Test
    fun `a working stream puts the next wait back to the shortest`() {
        val backoff = ReconnectBackoff(noJitter)
        repeat(6) { backoff.next() }

        backoff.reset()

        assertEquals(1_000L, backoff.next())
    }

    @Test
    fun `every wait stays within a fifth of its base`() {
        val backoff = ReconnectBackoff(Random(7))

        val first = backoff.next()
        repeat(10) { backoff.next() }
        val capped = backoff.next()

        assertTrue(first in 800L..1_200L, "first wait was $first")
        assertTrue(capped in 24_000L..36_000L, "capped wait was $capped")
    }
}
