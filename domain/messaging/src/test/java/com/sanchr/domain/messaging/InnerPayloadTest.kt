package com.sanchr.domain.messaging

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InnerPayloadTest {
    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun `encodes the snake_case keys iOS reads`() {
        val json =
            String(
                InnerPayload(
                    conversationId = "c-1",
                    messageId = "m-1",
                    contentType = "text",
                    content = "hi".toByteArray(),
                    senderProfileKey = key,
                    senderUserId = "u-1",
                    senderDeviceId = 3,
                ).encode(),
                Charsets.UTF_8,
            )
        listOf(
            "\"v\":1",
            "\"conversation_id\":\"c-1\"",
            "\"message_id\":\"m-1\"",
            "\"content_type\":\"text\"",
            "\"sender_user_id\":\"u-1\"",
            "\"sender_device_id\":3",
        ).forEach { assertTrue(json.contains(it), "missing $it in $json") }
        // Swift's JSONEncoder writes Data as base64; "hi" is "aGk=".
        assertTrue(json.contains("\"content\":\"aGk=\""), json)
    }

    @Test
    fun `round trips every field`() {
        val original =
            InnerPayload(
                conversationId = "c-1",
                messageId = "m-1",
                contentType = "receipt/v1",
                content = byteArrayOf(1, 2, 3),
                isSync = true,
                expiresAfterSecs = 86_400,
                senderProfileKey = key,
                senderUserId = "u-1",
                senderDeviceId = 2,
                replyToMessageId = "m-0",
            )
        val decoded = InnerPayload.decode(original.encode())!!
        assertEquals(original.conversationId, decoded.conversationId)
        assertEquals(original.contentType, decoded.contentType)
        assertContentEquals(original.content, decoded.content)
        assertContentEquals(key, decoded.senderProfileKey)
        assertEquals(true, decoded.isSync)
        assertEquals(86_400, decoded.expiresAfterSecs)
        assertEquals("m-0", decoded.replyToMessageId)
    }

    @Test
    fun `omitted optional fields decode as null`() {
        val decoded =
            InnerPayload.decode(
                """{"v":1,"conversation_id":"c","content_type":"text","content":"aGk="}""".toByteArray(),
            )!!
        assertNull(decoded.messageId)
        assertNull(decoded.senderProfileKey)
        assertNull(decoded.expiresAfterSecs)
        assertEquals(false, decoded.isSync)
    }

    @Test
    fun `recognises a payload by its version key`() {
        assertTrue(InnerPayload.isInnerPayload("""{"v":1,"content_type":"text"}""".toByteArray()))
        assertFalse(InnerPayload.isInnerPayload("plain legacy text".toByteArray()))
        assertFalse(InnerPayload.isInnerPayload("""{"content_type":"text"}""".toByteArray()))
        assertFalse(InnerPayload.isInnerPayload(ByteArray(0)))
    }

    @Test
    fun `malformed json decodes to null rather than throwing`() {
        assertNull(InnerPayload.decode("""{"v":1,""".toByteArray()))
        assertNull(InnerPayload.decode("not json".toByteArray()))
    }

    @Test
    fun `an unknown key does not break decoding`() {
        // A future iOS build may add fields; an old Android client must not choke.
        val decoded =
            InnerPayload.decode(
                """{"v":1,"conversation_id":"c","content_type":"text","content":"aGk=","future_field":true}""".toByteArray(),
            )
        assertEquals("text", decoded?.contentType)
    }

    @Test
    fun `equals and hashCode compare ByteArray fields by content`() {
        val a = InnerPayload(conversationId = "c", contentType = "text", content = byteArrayOf(1, 2, 3), senderProfileKey = key)
        val b = InnerPayload(conversationId = "c", contentType = "text", content = byteArrayOf(1, 2, 3), senderProfileKey = key.copyOf())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val differentContent = a.copy(content = byteArrayOf(9, 9, 9))
        assertTrue(a != differentContent)

        val differentKey = a.copy(senderProfileKey = ByteArray(32) { (it + 1).toByte() })
        assertTrue(a != differentKey)
    }

    @Test
    fun `a null sender profile key is not equal to a present but empty one`() {
        val withNullKey = InnerPayload(conversationId = "c", contentType = "text", content = byteArrayOf(1), senderProfileKey = null)
        val withEmptyKey =
            InnerPayload(conversationId = "c", contentType = "text", content = byteArrayOf(1), senderProfileKey = ByteArray(0))
        assertTrue(withNullKey != withEmptyKey)
    }

    @Test
    fun `encode omits null optionals rather than writing null literals`() {
        // Android is the producer here; iOS is the reader. This is the direction
        // that actually protects the wire contract, so it must be pinned directly
        // rather than only inferred from the decode-side tests above.
        val json =
            String(
                InnerPayload(conversationId = "c-1", contentType = "text", content = byteArrayOf(1)).encode(),
                Charsets.UTF_8,
            )
        assertFalse(json.contains("null"), json)
        listOf(
            "\"message_id\"",
            "\"expires_after_secs\"",
            "\"sender_profile_key\"",
            "\"sender_user_id\"",
            "\"sender_device_id\"",
            "\"reply_to_message_id\"",
        ).forEach { assertFalse(json.contains(it), "unexpected $it present in $json") }
    }

    @Test
    fun `round trips bytes above 0x7F and explicitly empty byte arrays`() {
        // A profile key is 32 random bytes, so it very likely contains
        // high-bit values; every prior round-trip test happened to stay
        // below 0x7F and would not have caught a sign-extension mistake.
        val highBytes = byteArrayOf(0xFF.toByte(), 0x80.toByte(), 0x7F, 0x00)
        val withHighBytes =
            InnerPayload(
                conversationId = "c-1",
                contentType = "text",
                content = highBytes,
                senderProfileKey = highBytes,
            )
        val decodedHighBytes = InnerPayload.decode(withHighBytes.encode())!!
        assertContentEquals(highBytes, decodedHighBytes.content)
        assertContentEquals(highBytes, decodedHighBytes.senderProfileKey)

        val withEmptyArrays =
            InnerPayload(
                conversationId = "c-1",
                contentType = "text",
                content = ByteArray(0),
                senderProfileKey = ByteArray(0),
            )
        val decodedEmptyArrays = InnerPayload.decode(withEmptyArrays.encode())!!
        assertContentEquals(ByteArray(0), decodedEmptyArrays.content)
        assertContentEquals(ByteArray(0), decodedEmptyArrays.senderProfileKey)
    }
}
