package com.sanchr.app.data

import com.sanchr.core.model.MessageContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageContentCodecTest {
    @Test
    fun `an iOS image message decodes to Image with its encrypted attachment`() {
        val body =
            """{"image":{"_0":[{"url":"sanchr-media://m1","encryptionKey":"AQID","encryptionIV":"BAUG","mimeType":"image/jpeg","sizeBytes":100,"width":640,"height":480,"caption":"look"}]}}"""
        val content = MessageContentCodec.fromStored("image", body) as MessageContent.Image
        assertEquals("sanchr-media://m1", content.url)
        assertEquals(640, content.width)
        assertEquals("look", content.caption)
        assertEquals("m1", content.attachment!!.mediaId)
        assertNotNull(content.attachment!!.keyBytes())
    }

    @Test
    fun `iOS document and audio cases map to File and Voice`() {
        val doc =
            MessageContentCodec.fromStored(
                "document",
                """{"document":{"_0":[{"url":"sanchr-media://d","mimeType":"application/pdf","sizeBytes":9,"filename":"tax.pdf"}]}}""",
            )
        assertEquals("tax.pdf", (doc as MessageContent.File).fileName)
        val voice =
            MessageContentCodec.fromStored(
                "audio",
                """{"audio":{"_0":[{"url":"sanchr-media://a","mimeType":"audio/m4a","isVoiceMessage":true,"audioDurationMs":4200}]}}""",
            )
        assertEquals(4200L, (voice as MessageContent.Voice).durationMs)
    }

    @Test
    fun `text, location, and unparseable bodies stay visible`() {
        assertEquals(MessageContent.Text("hi"), MessageContentCodec.fromStored("text", "hi"))
        val loc = MessageContentCodec.fromStored("location", """{"latitude":1.5,"longitude":2.5}""") as MessageContent.Location
        assertEquals(1.5, loc.latitude, 0.0)
        assertTrue(MessageContentCodec.fromStored("image", "not json") is MessageContent.Text)
        assertTrue(MessageContentCodec.fromStored("sticker/v9", "{}") is MessageContent.Text)
    }
}
