package com.sanchr.feature.chats

import com.sanchr.core.model.Conversation
import com.sanchr.core.model.ConversationType
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.core.model.MessageStatus
import com.sanchr.core.model.User
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.Instant

class ChatsListPreviewTest {
    private fun user(
        id: String,
        name: String,
    ) = User(id = id, phoneNumber = "", displayName = name, avatarUrl = null, createdAt = Instant.fromEpochMilliseconds(0))

    private fun conversation(
        content: MessageContent?,
        sender: String = "peer",
        type: ConversationType = ConversationType.DIRECT,
    ) = Conversation(
        id = "c1",
        type = type,
        participants = listOf(user("self", "You"), user("peer", "Ravi Kumar")),
        lastMessage =
            content?.let {
                Message(
                    id = "m1",
                    conversationId = "c1",
                    senderId = sender,
                    content = it,
                    status = MessageStatus.DELIVERED,
                    timestamp = Instant.fromEpochMilliseconds(5),
                )
            },
        updatedAt = Instant.fromEpochMilliseconds(1),
        createdAt = Instant.fromEpochMilliseconds(1),
    )

    @Test
    fun `preview wording matches iOS per content type`() {
        assertEquals("No messages yet", conversation(null).previewText())
        assertEquals("hi", conversation(MessageContent.Text("hi")).previewText())
        assertEquals("Photo", conversation(MessageContent.Image("u", null, 1, 1)).previewText())
        assertEquals("Voice message", conversation(MessageContent.Voice("u", 1)).previewText())
        assertEquals("Document", conversation(MessageContent.File("u", "f", "m", 1)).previewText())
        assertEquals("Location", conversation(MessageContent.Location(1.0, 2.0)).previewText())
        assertEquals("Contact: Ada", conversation(MessageContent.Contact("Ada", "+1")).previewText())
        assertEquals("Viewed", conversation(MessageContent.System(MessageContent.System.VIEW_ONCE_CONSUMED)).previewText())
    }

    @Test
    fun `a group prefixes the incoming sender's first name, direct chats and own messages do not`() {
        assertEquals("Ravi: ", conversation(MessageContent.Text("x"), type = ConversationType.GROUP).senderPrefix("self"))
        assertNull(conversation(MessageContent.Text("x"), sender = "self", type = ConversationType.GROUP).senderPrefix("self"))
        assertNull(conversation(MessageContent.Text("x")).senderPrefix("self"))
    }

    @Test
    fun `timestamps read as time today, weekday this week, then a date`() {
        val now = 1_700_000_000_000L // Tue 14 Nov 2023 22:13 UTC
        val locale = Locale.US
        assertEquals(formatChatTimestamp(now, now, locale).length > 0, true)
        assertEquals(true, formatChatTimestamp(now - 2 * 86_400_000L, now, locale).length == 3)
        assertEquals(true, formatChatTimestamp(now - 30 * 86_400_000L, now, locale).matches(Regex("\\d+ [A-Za-z]{3}")))
        assertEquals(true, formatChatTimestamp(now - 400 * 86_400_000L, now, locale).matches(Regex("\\d+ [A-Za-z]{3} \\d{4}")))
    }
}
