package com.sanchr.domain.messaging

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * [ReceiptJitter] itself is never exercised by [SendReadReceiptUseCaseTest] —
 * every test there stubs `await()` as a no-op so the RNG range is a
 * property that would otherwise be correct only by reading. That range is a
 * privacy property (it exists so the server cannot correlate read-time with
 * identity), not a nicety: a bound that silently became a constant, or an
 * off-by-one that made it always zero, would defeat it while every
 * `SendReadReceiptUseCaseTest` stayed green.
 *
 * `runTest`'s virtual clock advances through `delay()` without a real
 * sleep, so `testScheduler.currentTime` before/after `await()` reports the exact
 * millisecond delay drawn on each of the many iterations below.
 */
class ReceiptJitterTest {
    @Test
    fun `await delays within 0 to 3000ms inclusive and the delay actually varies`() =
        runTest {
            val jitter = ReceiptJitter()
            val observedDelaysMs = mutableSetOf<Long>()

            repeat(200) {
                val before = testScheduler.currentTime
                jitter.await()
                val delayMs = testScheduler.currentTime - before
                assertTrue(delayMs in 0L..3_000L, "jitter delay ${delayMs}ms fell outside [0, 3000]")
                observedDelaysMs += delayMs
            }

            assertTrue(
                observedDelaysMs.size > 1,
                "jitter must vary across calls, not collapse to a single constant value",
            )
        }
}
