package com.sanchr.feature.chats.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedContentParserTest {
    @Test
    fun `shared text becomes a text message`() {
        assertEquals(SharedContent.Text("hello"), SharedContentParser.parse(emptyList(), "hello"))
    }

    @Test
    fun `a shared link is text, because Android sends links as text`() {
        assertEquals(
            SharedContent.Text("https://example.com/a"),
            SharedContentParser.parse(emptyList(), "https://example.com/a"),
        )
    }

    @Test
    fun `text alongside files is offered as the caption, not a second message`() {
        assertEquals(
            SharedContent.Attachments(listOf("content://a"), caption = "on the beach"),
            SharedContentParser.parse(listOf("content://a"), "on the beach"),
        )
    }

    @Test
    fun `several files come through in the order they were shared`() {
        assertEquals(
            SharedContent.Attachments(listOf("content://a", "content://b")),
            SharedContentParser.parse(listOf("content://a", "content://b"), null),
        )
    }

    @Test
    fun `blank uris and whitespace-only text are dropped`() {
        assertEquals(SharedContent.Attachments(listOf("content://a")), SharedContentParser.parse(listOf("content://a", " ", ""), "   "))
    }

    @Test
    fun `subject is used only when there is no text`() {
        assertEquals(SharedContent.Text("An article"), SharedContentParser.parse(emptyList(), null, subject = "An article"))
        assertEquals(SharedContent.Text("the body"), SharedContentParser.parse(emptyList(), "the body", subject = "An article"))
    }

    @Test
    fun `a share carrying nothing sendable parses to nothing`() {
        assertNull(SharedContentParser.parse(emptyList(), null))
        assertNull(SharedContentParser.parse(listOf(""), "  ", subject = " "))
    }
}
