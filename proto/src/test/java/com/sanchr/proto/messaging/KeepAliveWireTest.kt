package com.sanchr.proto.messaging

import kotlin.test.Test
import kotlin.test.assertEquals
import sanchr.messaging.Messaging

class KeepAliveWireTest {
    /**
     * The server matches on the event and does nothing when there is none, so
     * a keepalive must reach it as exactly that. Anything set here would be
     * acted on.
     */
    @Test
    fun `a keepalive is a client event with no event set`() {
        val wire = ClientEvent.KeepAlive.toProto()

        assertEquals(Messaging.ClientEvent.EventCase.EVENT_NOT_SET, wire.eventCase)
        assertEquals(0, wire.serializedSize)
    }
}
