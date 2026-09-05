package com.sanchr.proto.calling

import com.google.protobuf.ByteString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import sanchr.calling.Calling

class CallingMappersTest {
    private val ct1 = byteArrayOf(1, 10, 11)
    private val ct2 = byteArrayOf(2, 20, 22)

    @Test
    fun `offer fans out per device and mirrors the device-1 entry into the legacy field`() {
        val proto =
            CallOffer(
                recipientId = "bob",
                callType = "video",
                deviceOffers = listOf(DeviceCallOffer(2, ct2), DeviceCallOffer(1, ct1)),
            ).toProto()

        assertEquals("bob", proto.recipientId)
        assertEquals("video", proto.callType)
        assertEquals(listOf(2, 1), proto.deviceOffersList.map { it.deviceId })
        assertContentEquals(ct2, proto.deviceOffersList[0].encryptedSdpPayload.toByteArray())
        assertContentEquals(ct1, proto.encryptedSdpPayload.toByteArray())
        assertTrue(proto.deliveryToken.isEmpty)
    }

    @Test
    fun `with no device 1 the legacy field carries the lowest device`() {
        val offer = CallOffer("bob", "voice", listOf(DeviceCallOffer(5, ct2), DeviceCallOffer(3, ct1)))
        assertContentEquals(ct1, offer.legacyEncryptedSdpPayload)
    }

    @Test
    fun `every stream payload round-trips through the oneof`() {
        val frames =
            listOf(
                CallSignal("c", 2, CallSignalPayload.Join("callee", answererDevice = 2)),
                CallSignal("c", 2, CallSignalPayload.IceCandidate("""{"candidate":"x"}""".toByteArray())),
                CallSignal("c", 2, CallSignalPayload.Control("ringing")),
                CallSignal("c", 2, CallSignalPayload.EncryptedSdpAnswer(ct1)),
            )
        val back = frames.map { it.toProto().toModel() }

        val join = assertIs<CallSignalPayload.Join>(back[0]!!.payload)
        assertEquals("callee", join.role)
        assertEquals(2, join.answererDevice)
        assertContentEquals("""{"candidate":"x"}""".toByteArray(), assertIs<CallSignalPayload.IceCandidate>(back[1]!!.payload).json)
        assertEquals("ringing", assertIs<CallSignalPayload.Control>(back[2]!!.payload).action)
        assertContentEquals(ct1, assertIs<CallSignalPayload.EncryptedSdpAnswer>(back[3]!!.payload).ciphertext)
        assertTrue(back.all { it!!.callId == "c" && it.peerDevice == 2 })
    }

    @Test
    fun `a frame with no payload maps to null rather than a bogus signal`() {
        assertNull(
            Calling.CallSignal
                .newBuilder()
                .setCallId("c")
                .build()
                .toModel(),
        )
    }

    @Test
    fun `history and TURN map every field`() {
        val history =
            Calling.GetCallHistoryResponse
                .newBuilder()
                .addEntries(
                    Calling.CallLogEntry
                        .newBuilder()
                        .setCallId("c1")
                        .setPeerId("alice")
                        .setPeerName("Sanchr User")
                        .setCallType("voice")
                        .setDirection("incoming")
                        .setStatus("missed")
                        .setStartedAt(100)
                        .setEndedAt(160)
                        .setDurationSecs(60),
                ).build()
                .toModel()
        assertEquals(
            CallLogEntry("c1", "alice", "Sanchr User", "voice", "incoming", "missed", 100, 160, 60),
            history.entries.single(),
        )

        val turn =
            Calling.TurnCredentials
                .newBuilder()
                .addUrls("turn:t.example:3478")
                .setUsername("u")
                .setCredential("p")
                .setTtl(600)
                .build()
                .toModel()
        assertEquals(TurnCredentials(listOf("turn:t.example:3478"), "u", "p", 600), turn)
    }

    @Test
    fun `encrypted answer bytes are carried untouched`() {
        val proto = CallSignal("c", 1, CallSignalPayload.EncryptedSdpAnswer(ct2)).toProto()
        assertEquals(ByteString.copyFrom(ct2), proto.encryptedSdpAnswer)
        assertTrue(proto.hasEncryptedSdpAnswer())
    }
}
