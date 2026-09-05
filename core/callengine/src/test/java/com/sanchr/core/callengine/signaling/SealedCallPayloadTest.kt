package com.sanchr.core.callengine.signaling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SealedCallPayloadTest {
    private val sdp = "v=0\r\no=- 1 1 IN IP4 0.0.0.0\r\na=fingerprint:sha-256 AA:BB:CC\r\na=setup:actpass\r\n"

    @Test
    fun `encodes with the iOS key names`() {
        val json = String(SealedCallPayload(sdp, "answer", "sha-256 AA:BB:CC", 1700000000.5).encode())
        assertTrue(json.contains("\"dtls_fingerprint\":\"sha-256 AA:BB:CC\""), json)
        assertTrue(json.contains("\"timestamp\":1.7000000005E9") || json.contains("\"timestamp\":1700000000.5"), json)
        assertTrue(json.contains("\"type\":\"answer\""), json)
        assertFalse(json.contains("dtlsFingerprint"))
    }

    @Test
    fun `type is omitted when null and tolerated when absent, as older iOS payloads`() {
        val json = String(SealedCallPayload(sdp, null, "x", 1.0).encode())
        assertFalse(json.contains("\"type\""), json)
        val decoded = SealedCallPayload.decode("""{"sdp":"v=0","dtls_fingerprint":"x","timestamp":1}""".toByteArray())
        assertNull(decoded.type)
        assertEquals(1.0, decoded.timestamp)
    }

    @Test
    fun `decodes what iOS JSONEncoder produces and ignores unknown keys`() {
        val decoded =
            SealedCallPayload.decode(
                """{"sdp":"v=0\r\n","type":"offer","dtls_fingerprint":"sha-256 AA","timestamp":1700000000.123456,"future":true}"""
                    .toByteArray(),
            )
        assertEquals("offer", decoded.type)
        assertEquals("sha-256 AA", decoded.dtlsFingerprint)
    }

    @Test
    fun `fingerprint extraction matches the iOS line scan`() {
        assertEquals("sha-256 AA:BB:CC", SealedCallPayload.fingerprintOf(sdp))
        assertNull(SealedCallPayload.fingerprintOf("v=0\r\na=setup:actpass\r\n"))
        assertEquals("sha-256 DD", SealedCallPayload.fingerprintOf("a=fingerprint: sha-256 DD \n"))
    }
}
