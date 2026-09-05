package com.sanchr.core.callengine.signaling

import com.sanchr.core.crypto.DeviceCallCiphertext
import com.sanchr.core.crypto.SignalSessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CallSignalingCryptoTest {
    private val signal = mockk<SignalSessionManager>()
    private var now = 1_700_000_000.0
    private val crypto = CallSignalingCrypto(signal) { now }
    private val sdp = "v=0\r\na=fingerprint:sha-256 AA:BB\r\n"

    @Test
    fun `sealOffer fans the sealed payload out per device with the SDP's own fingerprint`() =
        runTest {
            val plaintext = slot<ByteArray>()
            coEvery { signal.encryptCallOffers(capture(plaintext), "bob") } returns
                listOf(DeviceCallCiphertext(1, byteArrayOf(1)), DeviceCallCiphertext(2, byteArrayOf(2)))

            val offers = crypto.sealOffer(sdp, "bob")

            assertEquals(listOf(1, 2), offers.map { it.deviceId })
            val payload = SealedCallPayload.decode(plaintext.captured)
            assertEquals(sdp, payload.sdp)
            assertEquals("sha-256 AA:BB", payload.dtlsFingerprint)
            assertEquals(now, payload.timestamp)
            assertEquals(null, payload.type)
        }

    @Test
    fun `sealAnswer targets the caller's device, defaulting zero to 1`() =
        runTest {
            coEvery { signal.encryptForCall(any(), "alice", 1) } returns byteArrayOf(9)

            val ct = crypto.sealAnswer(sdp, "answer", "alice", deviceId = 0)

            assertContentEquals(byteArrayOf(9), ct)
            coVerify { signal.encryptForCall(match { SealedCallPayload.decode(it).type == "answer" }, "alice", 1) }
        }

    @Test
    fun `a local SDP without a fingerprint is refused before anything is encrypted`() =
        runTest {
            assertFailsWith<CallPayloadException> { crypto.sealOffer("v=0\r\n", "bob") }
            coVerify(exactly = 0) { signal.encryptCallOffers(any(), any()) }
        }

    @Test
    fun `open verifies age and fingerprint`() =
        runTest {
            val good = SealedCallPayload(sdp, "answer", "sha-256 AA:BB", now - 5).encode()
            coEvery { signal.decryptForCall(byteArrayOf(1), "alice", 2) } returns good
            assertEquals(sdp, crypto.open(byteArrayOf(1), "alice", 2, maxAgeSeconds = 30.0).sdp)

            val stale = SealedCallPayload(sdp, "answer", "sha-256 AA:BB", now - 120).encode()
            coEvery { signal.decryptForCall(byteArrayOf(2), "alice", 2) } returns stale
            val e1 = assertFailsWith<CallPayloadException> { crypto.open(byteArrayOf(2), "alice", 2, 30.0) }
            assertTrue(e1.message!!.contains("stale"))

            val swapped = SealedCallPayload(sdp, "answer", "sha-256 CC:DD", now).encode()
            coEvery { signal.decryptForCall(byteArrayOf(3), "alice", 2) } returns swapped
            val e2 = assertFailsWith<CallPayloadException> { crypto.open(byteArrayOf(3), "alice", 2, 30.0) }
            assertTrue(e2.message!!.contains("fingerprint"))

            val noFingerprint = SealedCallPayload("v=0\r\n", "answer", "sha-256 AA:BB", now).encode()
            coEvery { signal.decryptForCall(byteArrayOf(4), "alice", 2) } returns noFingerprint
            assertFailsWith<CallPayloadException> { crypto.open(byteArrayOf(4), "alice", 2, 30.0) }
        }

    @Test
    fun `decrypt failures and non-JSON plaintext surface as CallPayloadException`() =
        runTest {
            coEvery { signal.decryptForCall(byteArrayOf(5), "alice", 1) } throws IllegalStateException("no session")
            assertFailsWith<CallPayloadException> { crypto.open(byteArrayOf(5), "alice", 0, 30.0) }

            coEvery { signal.decryptForCall(byteArrayOf(6), "alice", 1) } returns "garbage".toByteArray()
            assertFailsWith<CallPayloadException> { crypto.open(byteArrayOf(6), "alice", 1, 30.0) }

            assertFailsWith<CallPayloadException> { crypto.open(ByteArray(0), "alice", 1, 30.0) }
        }
}
