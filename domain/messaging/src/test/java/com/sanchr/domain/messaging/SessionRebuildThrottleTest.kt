package com.sanchr.domain.messaging

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * One rebuild answers any number of undecryptable messages. Each rebuild
 * fetches a bundle, and each bundle takes one of the sender's one-time
 * pre-keys.
 */
class SessionRebuildThrottleTest {
    private var clock = 0L
    private val throttle = SessionRebuildThrottle(cooldownMillis = 600_000) { clock }

    @Test
    fun `a burst of failures from one device starts one rebuild`() {
        assertTrue(throttle.tryStart("alice", 1))

        repeat(9) { assertFalse(throttle.tryStart("alice", 1)) }
    }

    @Test
    fun `finishing does not open the door again straight away`() {
        // The message is delivered again on the next sync; that must not be
        // a fresh rebuild each time.
        assertTrue(throttle.tryStart("alice", 1))
        throttle.finished("alice", 1)

        clock += 60_000
        assertFalse(throttle.tryStart("alice", 1))
    }

    @Test
    fun `after the cooldown a device that is still failing is tried again`() {
        assertTrue(throttle.tryStart("alice", 1))
        throttle.finished("alice", 1)

        clock += 600_000
        assertTrue(throttle.tryStart("alice", 1))
    }

    @Test
    fun `a rebuild still running is not doubled, however long it takes`() {
        assertTrue(throttle.tryStart("alice", 1))

        clock += 3_600_000
        assertFalse(throttle.tryStart("alice", 1))
    }

    @Test
    fun `each device and each person is its own`() {
        assertTrue(throttle.tryStart("alice", 1))

        assertTrue(throttle.tryStart("alice", 2))
        assertTrue(throttle.tryStart("bob", 1))
    }

    @Test
    fun `a rebuild that failed is still not retried until the cooldown`() {
        // A failed attempt has fetched a bundle just the same.
        assertTrue(throttle.tryStart("alice", 1))
        throttle.finished("alice", 1)

        assertFalse(throttle.tryStart("alice", 1))
    }
}
