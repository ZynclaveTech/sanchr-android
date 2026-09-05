package com.sanchr.core.notifications

import com.sanchr.core.common.calls.IncomingCallEvents
import com.sanchr.core.common.calls.IncomingCallPush
import com.sanchr.core.common.calls.StreamWaker
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushRouterTest {
    private val drain = mockk<MessageDrainScheduler>(relaxed = true)
    private val calls = mockk<IncomingCallEvents>(relaxed = true)
    private val waker = mockk<StreamWaker>(relaxed = true)
    private val router = PushRouter(drain, calls, waker)

    @Test
    fun `payload parsing keeps the wire's call fields and treats a call hint without an id as a wake`() {
        val call =
            PushPayload.fromData(
                mapOf(
                    "type" to "wake",
                    "hint" to "call",
                    "call_id" to "c1",
                    "caller_id" to "alice",
                    "call_type" to "video",
                    "caller_device" to "2",
                ),
            )!!
        assertTrue(call.isCall)
        assertEquals(IncomingCallPush("c1", "alice", "video", 2), call.call)

        val bare = PushPayload.fromData(mapOf("type" to "wake", "hint" to "call"))!!
        assertTrue(!bare.isCall)
        assertNull(bare.call)
        assertNull(PushPayload.fromData(mapOf("hint" to "message")))
    }

    @Test
    fun `a message wake drains, a call wake rings and reopens the stream instead`() =
        runTest {
            router.route(PushPayload.fromData(mapOf("type" to "wake", "hint" to "message")))
            verify(exactly = 1) { drain.enqueueDrain() }
            coVerify(exactly = 0) { calls.onCallPush(any()) }

            router.route(
                PushPayload.fromData(
                    mapOf(
                        "type" to "wake",
                        "hint" to "call",
                        "call_id" to "c1",
                        "caller_id" to "alice",
                        "call_type" to "voice",
                        "caller_device" to "1",
                    ),
                ),
            )
            coVerify { calls.onCallPush(IncomingCallPush("c1", "alice", "voice", 1)) }
            verify { waker.wakeForCall("c1") }
            verify(exactly = 1) { drain.enqueueDrain() }
        }

    @Test
    fun `unknown types do nothing`() =
        runTest {
            router.route(PushPayload.fromData(mapOf("type" to "marketing")))
            router.route(null)
            verify(exactly = 0) { drain.enqueueDrain() }
            coVerify(exactly = 0) { calls.onCallPush(any()) }
        }
}
