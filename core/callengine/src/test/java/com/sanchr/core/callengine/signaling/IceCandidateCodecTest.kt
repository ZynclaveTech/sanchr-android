package com.sanchr.core.callengine.signaling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.webrtc.IceCandidate

class IceCandidateCodecTest {
    @Test
    fun `round-trips and uses the keys iOS reads`() {
        val bytes = IceCandidateCodec.encode(IceCandidate("audio", 0, "candidate:1 1 udp 2113937151 10.0.0.2 5000 typ host"))
        val json = String(bytes)
        assertTrue(json.contains("\"candidate\":\"candidate:1 1 udp"), json)
        assertTrue(json.contains("\"sdpMid\":\"audio\""), json)
        assertTrue(json.contains("\"sdpMLineIndex\":0"), json)

        val back = requireNotNull(IceCandidateCodec.decode(bytes))
        assertEquals("audio", back.sdpMid)
        assertEquals(0, back.sdpMLineIndex)
        assertEquals("candidate:1 1 udp 2113937151 10.0.0.2 5000 typ host", back.sdp)
    }

    @Test
    fun `a candidate without sdpMid decodes, junk does not`() {
        val back = IceCandidateCodec.decode("""{"candidate":"c","sdpMLineIndex":1}""".toByteArray())
        assertEquals(1, requireNotNull(back).sdpMLineIndex)
        assertNull(IceCandidateCodec.decode("not json".toByteArray()))
        assertNull(IceCandidateCodec.decode("""{"sdpMid":"a"}""".toByteArray()))
    }
}
