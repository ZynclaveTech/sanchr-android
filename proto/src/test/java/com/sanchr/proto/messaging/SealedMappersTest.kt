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
}
