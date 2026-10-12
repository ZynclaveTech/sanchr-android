package com.sanchr.proto.messaging

import com.google.protobuf.ByteString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import sanchr.messaging.Messaging

class SealedMappersTest {
    @Test
    fun `a sealed device message maps every field onto the proto`() {
        val proto =
            SealedDeviceMessage(
                recipientId = "u-1",
                deviceId = 3,
                sealedEnvelope = byteArrayOf(9, 8, 7),
                conversationId = "c-1",
                silent = true,
                recipientRegistrationId = 4242,
            ).toProtoForTest()
        assertEquals("u-1", proto.recipientId)
        assertEquals(3, proto.deviceId)
        assertContentEquals(byteArrayOf(9, 8, 7), proto.sealedEnvelope.toByteArray())
        assertEquals("c-1", proto.conversationId)
        assertEquals(true, proto.silent)
        assertEquals(4242, proto.recipientRegistrationId)
    }

    @Test
    fun `no registration id is stated unless the sender has one`() {
        // Zero is what the server reads as nothing to check.
        assertEquals(0, SealedDeviceMessage("u-1", 1, ByteArray(0)).toProtoForTest().recipientRegistrationId)
    }

    @Test
    fun `a refused send comes back naming the devices that were set up again`() {
        val response =
            Messaging.SendSealedMessageResponse
                .newBuilder()
                .setServerTimestamp(5)
                .addStaleDevices(
                    Messaging.StaleDevice
                        .newBuilder()
                        .setRecipientId("u-1")
                        .setDeviceId(2)
                        .setRegistered(true),
                ).addStaleDevices(
                    Messaging.StaleDevice
                        .newBuilder()
                        .setRecipientId("u-2")
                        .setDeviceId(1),
                ).build()
                .toManualForTest()

        assertEquals(
            listOf(StaleDevice("u-1", 2, registered = true), StaleDevice("u-2", 1, registered = false)),
            response.staleDevices,
        )
    }

    @Test
    fun `an accepted send names none`() {
        val response =
            Messaging.SendSealedMessageResponse
                .newBuilder()
                .setServerTimestamp(5)
                .build()
                .toManualForTest()

        assertEquals(emptyList(), response.staleDevices)
        assertEquals(5, response.serverTimestamp)
    }

    @Test
    fun `silent defaults to false so an ordinary message still alerts`() {
        assertFalse(SealedDeviceMessage("u-1", 1, ByteArray(0)).toProtoForTest().silent)
    }

    @Test
    fun `delivery tokens come back as byte arrays`() {
        val response =
            Messaging.DeliveryTokenResponse
                .newBuilder()
                .addTokens(ByteString.copyFrom(ByteArray(32) { 1 }))
                .addTokens(ByteString.copyFrom(ByteArray(32) { 2 }))
                .build()
                .toManualForTest()
        assertEquals(2, response.tokens.size)
        assertContentEquals(ByteArray(32) { 1 }, response.tokens[0])
    }

    /**
     * These were logged as "unhandled" and dropped, so a sealed message was
     * never received as it was sent, only at the next sync.
     */
    @Test
    fun `a sealed message on the stream is delivered, in the shape a sync gives it`() {
        val event =
            Messaging.ServerEvent
                .newBuilder()
                .setSealedMessage(
                    Messaging.SealedInboundMessage
                        .newBuilder()
                        .setSealedEnvelope(ByteString.copyFrom(byteArrayOf(1, 2, 3)))
                        .setServerTimestamp(42)
                        .setMessageId("m-1"),
                ).build()

        val mapped = event.toManual()

        val envelope = (mapped as ServerEvent.Message).envelope!!
        assertContentEquals(byteArrayOf(1, 2, 3), envelope.cipherText)
        assertEquals("m-1", envelope.messageId)
        assertEquals(42L, envelope.serverTimestamp)
        // What the receive path tells a sealed envelope by.
        assertEquals("sealed", envelope.contentType)
        assertEquals("00000000-0000-0000-0000-000000000000", envelope.senderId)
        assertEquals(0, envelope.senderDevice)
        assertEquals("00000000-0000-0000-0000-000000000000", envelope.conversationId)
    }
}
