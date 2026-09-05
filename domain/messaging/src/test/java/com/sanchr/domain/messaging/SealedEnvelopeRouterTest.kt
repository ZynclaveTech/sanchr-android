package com.sanchr.domain.messaging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SealedEnvelopeRouterTest {
    @Test
    fun `a receipt v1 payload routes to Control`() {
        val payload = InnerPayload(conversationId = "c-1", contentType = "receipt/v1", content = "ignored".toByteArray())

        val routed = SealedEnvelopeRouter.route(payload.encode(), fallbackContentType = "text")

        assertTrue(routed is RoutedPayload.Control)
        assertEquals("receipt/v1", routed.contentType)
        assertEquals(payload, routed.payload)
    }

    @Test
    fun `a profile-key v1 payload routes to Control`() {
        val payload = InnerPayload(conversationId = "c-1", contentType = "profile-key/v1", content = "ignored".toByteArray())

        val routed = SealedEnvelopeRouter.route(payload.encode(), fallbackContentType = "text")

        assertTrue(routed is RoutedPayload.Control)
        assertEquals("profile-key/v1", routed.contentType)
    }

    @Test
    fun `an inner payload with a text content type routes to UserMessage carrying the payload's content and ids`() {
        val payload =
            InnerPayload(
                conversationId = "c-1",
                messageId = "m-1",
                contentType = "text",
                content = "hello".toByteArray(),
            )

        val routed = SealedEnvelopeRouter.route(payload.encode(), fallbackContentType = "ignored")

        assertTrue(routed is RoutedPayload.UserMessage)
        assertEquals("hello", routed.content)
        assertEquals("text", routed.contentType)
        assertEquals("c-1", routed.conversationId)
        assertEquals("m-1", routed.messageId)
    }

    @Test
    fun `a disappearing timer on the payload reaches UserMessage`() {
        val payload =
            InnerPayload(
                conversationId = "c-1",
                messageId = "m-1",
                contentType = "text",
                content = "hello".toByteArray(),
                expiresAfterSecs = 86_400L,
            )

        val routed = SealedEnvelopeRouter.route(payload.encode(), fallbackContentType = "ignored")

        assertTrue(routed is RoutedPayload.UserMessage)
        assertEquals(86_400L, routed.expiresAfterSecs)
    }

    @Test
    fun `a zero or negative timer is treated as no timer, not an expired one`() {
        listOf(0L, -1L).forEach { wireValue ->
            val payload =
                InnerPayload(
                    conversationId = "c-1",
                    contentType = "text",
                    content = "hello".toByteArray(),
                    expiresAfterSecs = wireValue,
                )

            val routed = SealedEnvelopeRouter.route(payload.encode(), fallbackContentType = "ignored")

            assertTrue(routed is RoutedPayload.UserMessage)
            // Anything else would turn a sender with the feature off into a
            // deadline already in the past, deleting the message on arrival.
            assertNull(routed.expiresAfterSecs, "wire value $wireValue must mean no timer")
        }
    }

    @Test
    fun `legacy bare text carries no timer`() {
        val routed = SealedEnvelopeRouter.route("plain".toByteArray(), fallbackContentType = "text")

        assertTrue(routed is RoutedPayload.UserMessage)
        assertNull(routed.expiresAfterSecs)
    }

    @Test
    fun `an inner payload with an unrecognised content type still routes to UserMessage, not dropped`() {
        val payload =
            InnerPayload(
                conversationId = "c-1",
                messageId = "m-1",
                contentType = "sticker/v9",
                content = "surprise".toByteArray(),
            )

        val routed = SealedEnvelopeRouter.route(payload.encode(), fallbackContentType = "ignored")

        assertTrue(routed is RoutedPayload.UserMessage)
        assertEquals("surprise", routed.content)
        assertEquals("sticker/v9", routed.contentType)
    }

    @Test
    fun `bytes that are not an inner payload route to UserMessage with the envelope's content type`() {
        // The bare UTF-8 text an older Android build sends, before InnerPayload existed.
        val legacyPlaintext = "hey there".toByteArray()

        val routed = SealedEnvelopeRouter.route(legacyPlaintext, fallbackContentType = "text")

        assertTrue(routed is RoutedPayload.UserMessage)
        assertEquals("hey there", routed.content)
        assertEquals("text", routed.contentType)
        assertEquals("", routed.conversationId)
        assertNull(routed.messageId)
    }

    @Test
    fun `valid JSON with no v key is legacy text, not a payload with defaulted fields`() {
        // "{}" decodes cleanly via InnerPayload.decode (every field defaults),
        // so the router must gate on isInnerPayload first or this would be
        // misrouted as an empty-content payload instead of legacy text.
        val legacyPlaintext = "{}".toByteArray()

        val routed = SealedEnvelopeRouter.route(legacyPlaintext, fallbackContentType = "text")

        assertTrue(routed is RoutedPayload.UserMessage)
        assertEquals("{}", routed.content)
        assertEquals("text", routed.contentType)
        assertEquals("", routed.conversationId)
        assertNull(routed.messageId)
    }

    @Test
    fun `the sender's profile key on the payload reaches UserMessage`() {
        val key = ByteArray(32) { 7 }
        val payload = InnerPayload(conversationId = "c-1", content = "hi".toByteArray(), senderProfileKey = key)
        val routed = SealedEnvelopeRouter.route(payload.encode(), fallbackContentType = "text")
        assertTrue(routed is RoutedPayload.UserMessage)
        assertTrue(key.contentEquals(routed.senderProfileKey))
    }

    @Test
    fun `legacy bare text carries no profile key`() {
        val routed = SealedEnvelopeRouter.route("plain".toByteArray(), fallbackContentType = "text")
        assertTrue(routed is RoutedPayload.UserMessage)
        assertNull(routed.senderProfileKey)
    }
}
