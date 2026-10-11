package com.sanchr.core.callengine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The server ends an unanswered call; this device's own timer is the
 * fallback. A fallback shorter than the server's limit is not a fallback: it
 * hangs up on a call the other phone is still ringing for.
 */
class CallTimeoutTest {
    /** `RINGING_TIMEOUT_SECS` and `MISSED_SWEEP_INTERVAL_SECS` in the backend's call service. */
    private val serverRingSeconds = 120L
    private val serverSweepSeconds = 5L

    @Test
    fun `the server's ring time is the one this app believes it is`() {
        assertEquals(serverRingSeconds * 1_000, CallManager.SERVER_RING_TIMEOUT_MS)
    }

    @Test
    fun `this device gives up only after the server has had its say`() {
        val serverLatest = (serverRingSeconds + serverSweepSeconds) * 1_000

        assertTrue(CallManager.CALL_TIMEOUT_MS > serverLatest, "the fallback must outlast the server's limit and its sweep")
    }
}
