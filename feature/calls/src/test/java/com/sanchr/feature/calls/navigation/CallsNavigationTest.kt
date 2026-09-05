package com.sanchr.feature.calls.navigation

import com.sanchr.feature.calls.OutgoingCallRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CallsNavigationTest {
    @Test
    fun `route arguments round-trip into an outgoing request`() {
        assertEquals(OutgoingCallRequest("peer-1", "Ada", isVideo = true), outgoingRequestFrom("video", "peer-1", "Ada"))
        assertEquals(OutgoingCallRequest("peer-1", "peer-1", isVideo = false), outgoingRequestFrom("voice", "peer-1", ""))
    }

    @Test
    fun `an incoming or unknown call has no outgoing request`() {
        assertNull(outgoingRequestFrom("answer", "peer-1", "Ada"))
        assertNull(outgoingRequestFrom("voice", null, "Ada"))
        assertNull(outgoingRequestFrom(null, null, null))
    }
}
