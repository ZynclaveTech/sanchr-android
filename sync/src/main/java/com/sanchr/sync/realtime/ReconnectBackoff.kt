package com.sanchr.sync.realtime

import kotlin.random.Random

/**
 * How long to wait before opening the message stream again after it drops.
 *
 * Doubles from a second up to half a minute, so a server that is down is not
 * hammered and one that blinked is rejoined at once. Each wait is spread by a
 * fifth either way: every phone that lost the same server would otherwise
 * come back in the same instant.
 */
class ReconnectBackoff(
    private val random: Random = Random.Default,
) {
    private var failures = 0

    /** The wait before the next attempt, in milliseconds. Longer each time it is asked. */
    fun next(): Long {
        val base = (FIRST_MS shl failures.coerceAtMost(MAX_DOUBLINGS)).coerceAtMost(CEILING_MS)
        failures++
        val spread = (base * JITTER).toLong()
        return base - spread + random.nextLong(2 * spread + 1)
    }

    /** The stream worked: the next drop starts from the shortest wait again. */
    fun reset() {
        failures = 0
    }

    companion object {
        const val FIRST_MS = 1_000L
        const val CEILING_MS = 30_000L
        private const val MAX_DOUBLINGS = 5
        private const val JITTER = 0.2
    }
}
