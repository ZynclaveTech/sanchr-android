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

    /**
     * Every key Android emits, pinned against the property names Swift's
     * `Message.MediaAttachment` encodes. These are not declared anywhere
     * shared: iOS uses Codable's default keys, Android its property names
     * (plus one `@SerialName` for `thumbnailURL`), so a rename on one side
     * is silent until a real message fails to decode on the other.
     */
    @Test
    fun `every attachment key Android emits matches iOS's property name`() {
        val everything =
            MediaAttachment(
                url = "sanchr-media://m1",
                encryptionKey = "AQID",
                encryptionIV = "BAUG",
                mimeType = "audio/mp4",
                sizeBytes = 1234,
                thumbnailUrl = "https://cdn/t.jpg",
                caption = "hi",
                width = 640,
                height = 480,
                durationSeconds = 12.5,
                blurHash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj",
                filename = "a.m4a",
                isVoiceMessage = true,
                audioDurationMs = 4200,
                audioWaveform = listOf(0.25f, 1.0f),
                isViewOnce = true,
            )

        val json = MediaContentEnvelope.encode(MediaKind.AUDIO, listOf(everything))

        listOf(
            "url",
            "encryptionKey",
            "encryptionIV",
            "mimeType",
            "sizeBytes",
            "thumbnailURL",
            "caption",
            "width",
            "height",
            "durationSeconds",
            "blurHash",
            "filename",
            "isVoiceMessage",
            "audioDurationMs",
            "audioWaveform",
            "isViewOnce",
        ).forEach { key ->
            assertTrue(json.contains("\"$key\":"), "missing key $key in $json")
        }
        // The Kotlin-side spellings must not leak onto the wire.
        assertTrue(!json.contains("\"thumbnailUrl\""), json)
        // Everything round-trips, so the pinned names are the ones we also read.
        val decoded = requireNotNull(MediaContentEnvelope.decode(json, null)).attachments.single()
        assertEquals(everything, decoded)
    }
}
