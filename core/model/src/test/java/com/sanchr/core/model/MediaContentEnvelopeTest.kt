package com.sanchr.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaContentEnvelopeTest {
    private val attachment =
        MediaAttachment(
            url = "sanchr-media://m-1",
            encryptionKey = "AQID",
            encryptionIV = "BAUG",
            mimeType = "image/jpeg",
            sizeBytes = 1234,
            caption = "hi",
            width = 640,
            height = 480,
            filename = "a.jpg",
        )

    @Test
    fun `encodes as the Swift enum shape with iOS key names`() {
        val json = MediaContentEnvelope.encode(MediaKind.IMAGE, listOf(attachment))
        assertTrue(json.startsWith("""{"image":{"_0":[{"""), json)
        assertTrue(json.contains("\"encryptionIV\":\"BAUG\""), json)
        assertTrue(json.contains("\"mimeType\":\"image/jpeg\""), json)
        assertTrue(json.contains("\"sizeBytes\":1234"), json)
        assertTrue(json.contains("\"filename\":\"a.jpg\""), json)
        // Optional fields that are null are omitted, as Swift would omit nil.
        assertTrue(!json.contains("thumbnailURL"), json)
        assertTrue(!json.contains("blurHash"), json)
    }

    @Test
    fun `decodes what iOS sends — list form, legacy single object, and unknown keys`() {
        val list =
            """{"video":{"_0":[{"url":"sanchr-media://v1","encryptionKey":"AA==","encryptionIV":"AA==","mimeType":"video/mp4","sizeBytes":9,"thumbnailURL":"https://cdn/t.jpg","durationSeconds":12.5,"future":1}]}}"""
        val d = requireNotNull(MediaContentEnvelope.decode(list, null))
        assertEquals(MediaKind.VIDEO, d.kind)
        assertEquals("v1", d.attachments.single().mediaId)
        assertEquals("https://cdn/t.jpg", d.attachments.single().thumbnailUrl)
        assertEquals(12.5, d.attachments.single().durationSeconds)

        val single = """{"document":{"_0":{"url":"sanchr-media://d1","mimeType":"application/pdf","filename":"x.pdf"}}}"""
        val s = requireNotNull(MediaContentEnvelope.decode(single, null))
        assertEquals(MediaKind.DOCUMENT, s.kind)
        assertEquals("x.pdf", s.attachments.single().filename)
    }

    @Test
    fun `a bare attachment object takes the kind from the content type, then from the mime`() {
        val bare = """{"url":"https://legacy/img.png","mimeType":"image/png"}"""
        assertEquals(MediaKind.DOCUMENT, MediaContentEnvelope.decode(bare, MediaKind.DOCUMENT)!!.kind)
        assertEquals(MediaKind.IMAGE, MediaContentEnvelope.decode(bare, null)!!.kind)
        assertNull(
            MediaContentEnvelope
                .decode(bare, null)!!
                .attachments
                .single()
                .mediaId,
        )
    }

    @Test
    fun `round trip and rejection of non-attachment bodies`() {
        val json = MediaContentEnvelope.encode(MediaKind.AUDIO, listOf(attachment.copy(mimeType = "audio/m4a", isVoiceMessage = true)))
        val back = requireNotNull(MediaContentEnvelope.decode(json, null))
        assertEquals(MediaKind.AUDIO, back.kind)
        assertEquals(true, back.attachments.single().isVoiceMessage)
        assertNull(MediaContentEnvelope.decode("just text", null))
        assertNull(MediaContentEnvelope.decode("""{"text":"x"}""", null))
        assertNull(MediaContentEnvelope.decode("""{"image":{"_0":[]}}""", null))
    }

    @Test
    fun `key bytes decode and media id parses`() {
        assertEquals(listOf<Byte>(1, 2, 3), attachment.keyBytes()!!.toList())
        assertEquals("m-1", attachment.mediaId)
        assertEquals("sanchr-media://abc", MediaAttachment.mediaUrl("abc"))
        assertEquals(MediaKind.DOCUMENT, MediaKind.forMimeType("application/zip"))
        assertEquals(MediaKind.AUDIO, MediaKind.fromWire("voice"))
    }
}
