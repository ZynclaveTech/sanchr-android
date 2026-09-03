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
            ).toProtoForTest()
        assertEquals("u-1", proto.recipientId)
        assertEquals(3, proto.deviceId)
        assertContentEquals(byteArrayOf(9, 8, 7), proto.sealedEnvelope.toByteArray())
        assertEquals("c-1", proto.conversationId)
        assertEquals(true, proto.silent)
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
