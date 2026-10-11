package com.sanchr.domain.messaging

import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.core.model.MessageStatus
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

class ChatTranscriptTest {
    private val utc = TimeZone.getTimeZone("UTC")

    /** 2026-10-11 12:00 UTC. */
    private val noon = 1_791_720_000_000L

    private fun message(
        sender: String,
        content: MessageContent,
        at: Long = noon,
        id: String = "m$at",
    ) = Message(
        id = id,
        conversationId = "c1",
        senderId = sender,
        content = content,
        status = MessageStatus.DELIVERED,
        timestamp = Instant.fromEpochMilliseconds(at),
    )

    private fun render(vararg messages: Message) = ChatTranscript.render("Ravi Kumar", messages.toList(), "self", noon + 3_600_000, utc)

    @Test
    fun `a chat reads as who said what and when, oldest first`() {
        val text =
            render(
                message("self", MessageContent.Text("see you at 6"), at = noon + 60_000),
                message("ravi", MessageContent.Text("on my way"), at = noon),
            )

        assertEquals(
            listOf(
                "Sanchr conversation with Ravi Kumar",
                "Exported 2026-10-11 13:00",
                "Text only — media is not included.",
                "",
                "[2026-10-11 12:00] Ravi Kumar: on my way",
                "[2026-10-11 12:01] You: see you at 6",
            ),
            text.lines(),
        )
    }

    /** It says media is left out, so nothing of the media may be in it. */
    @Test
    fun `media is named for what it was and nothing of it is included`() {
        val secret = MediaAttachment(url = "sanchr-media://abc", encryptionKey = "KEYKEYKEY", mimeType = "image/jpeg")
        val text =
            render(
                message(
                    "ravi",
                    MessageContent.Image("sanchr-media://abc", null, 1, 1, caption = "the view", attachment = secret),
                    at = noon,
                ),
                message("ravi", MessageContent.Album(listOf(secret, secret, secret)), at = noon + 1),
                message("ravi", MessageContent.Voice("sanchr-media://v", 4_000, attachment = secret), at = noon + 2),
                message("ravi", MessageContent.File("sanchr-media://f", "report.pdf", "application/pdf", 10, secret), at = noon + 3),
                message("ravi", MessageContent.Location(12.9, 77.6), at = noon + 4),
                message("ravi", MessageContent.Contact("Meera", "+911234567890"), at = noon + 5),
            )

        assertTrue("[photo] the view" in text)
        assertTrue("[3 photos and videos]" in text)
        assertTrue("[voice message]" in text)
        assertTrue("[document: report.pdf]" in text)
        assertTrue("[location]" in text)
        assertTrue("[contact: Meera]" in text)
        listOf("sanchr-media://", "KEYKEYKEY", "12.9", "77.6", "+911234567890").forEach { assertFalse(it in text, it) }
    }

    @Test
    fun `a video is called a video`() {
        val video = MediaAttachment(url = "sanchr-media://v", mimeType = "video/mp4")

        assertEquals("[video]", ChatTranscript.describe(MessageContent.Image("", null, 1, 1, attachment = video)))
        assertEquals("[video]", ChatTranscript.describe(MessageContent.File("", "clip.mp4", "video/mp4", 1, video)))
    }

    /** A name is the contact's to choose; it must not be able to forge a line. */
    @Test
    fun `a name with a line break in it stays on one line`() {
        val text =
            ChatTranscript.render(
                "Ravi\n[2026-01-01 00:00] You: I owe you money",
                listOf(message("ravi", MessageContent.Text("hi"))),
                "self",
                noon,
                utc,
            )

        assertEquals(5, text.lines().size)
        assertTrue(text.lines()[4].startsWith("[2026-10-11 12:00] Ravi [2026-01-01 00:00] You: I owe you money: hi"))
    }

    @Test
    fun `the file is named after the chat, safely`() {
        assertEquals("Sanchr-Ravi Kumar.txt", ChatTranscript.fileName("Ravi Kumar"))
        assertEquals("Sanchr-etc-passwd.txt", ChatTranscript.fileName("../../etc/passwd"))
        assertEquals("Sanchr-chat.txt", ChatTranscript.fileName("///"))
        assertEquals("Sanchr-chat.txt", ChatTranscript.fileName(""))
        assertTrue(ChatTranscript.fileName("x".repeat(500)).length < 80)
        assertFalse('/' in ChatTranscript.fileName("a/b\\c"))
    }
}
