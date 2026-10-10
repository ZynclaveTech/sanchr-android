package com.sanchr.feature.chats.shared

import com.sanchr.core.model.MediaAttachment
import com.sanchr.feature.chats.MessageStatus
import com.sanchr.feature.chats.MessageUiModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sorting a conversation into the three things people go looking for.
 *
 * Android had no way to find a photo from three weeks ago except scrolling to
 * it; iOS has had this browser throughout, and the split has to match or the
 * same message lands in different places on the two apps.
 */
class SharedContentTest {
    private fun attachment(url: String = "https://media.example/a.jpg") =
        MediaAttachment(url = url, mimeType = "image/jpeg", sizeBytes = 10)

    private fun message(
        id: String,
        contentType: String,
        timestamp: Long,
        text: String = "",
        attachment: MediaAttachment? = null,
        albumAttachments: List<MediaAttachment> = emptyList(),
        isViewOnce: Boolean = false,
    ) = MessageUiModel(
        id = id,
        text = text,
        timestamp = timestamp,
        isFromMe = false,
        status = MessageStatus.DELIVERED,
        contentType = contentType,
        attachment = attachment,
        albumAttachments = albumAttachments,
        isViewOnce = isViewOnce,
    )

    @Test
    fun `photos, files and links land in their own buckets`() {
        val buckets =
            SharedContent.partition(
                listOf(
                    message("photo", "image", 1_000, attachment = attachment()),
                    message("doc", "file", 2_000, attachment = attachment("https://media.example/a.pdf")),
                    message("link", "text", 3_000, text = "have a look at https://example.com/x"),
                    message("chat", "text", 4_000, text = "no link here"),
                ),
            )

        assertEquals(listOf("photo"), buckets.media.map { it.id })
        assertEquals(listOf("doc"), buckets.docs.map { it.id })
        assertEquals(listOf("link"), buckets.links.map { it.messageId })
    }

    @Test
    fun `each bucket is newest first`() {
        val buckets =
            SharedContent.partition(
                listOf(
                    message("old", "image", 1_000, attachment = attachment()),
                    message("new", "image", 9_000, attachment = attachment()),
                    message("middle", "image", 5_000, attachment = attachment()),
                ),
            )

        assertEquals(listOf("new", "middle", "old"), buckets.media.map { it.id })
    }

    @Test
    fun `an album contributes every tile, because the point is to find one picture`() {
        val buckets =
            SharedContent.partition(
                listOf(
                    message(
                        "album",
                        "album",
                        1_000,
                        attachment = attachment("https://media.example/0.jpg"),
                        albumAttachments = (0..2).map { attachment("https://media.example/$it.jpg") },
                    ),
                ),
            )

        assertEquals(listOf("album#0", "album#1", "album#2"), buckets.media.map { it.id }.sorted())
    }

    @Test
    fun `view-once media is in no bucket at all`() {
        // Listing it would be a second way to open something meant to be seen
        // once and wiped.
        val buckets =
            SharedContent.partition(
                listOf(message("once", "image", 1_000, attachment = attachment(), isViewOnce = true)),
            )

        assertTrue(buckets.media.isEmpty())
        assertTrue(buckets.isEmpty(SharedContent.Tab.MEDIA))
    }

    @Test
    fun `a photo with no attachment is not listed, since there is nothing to open`() {
        val buckets = SharedContent.partition(listOf(message("broken", "image", 1_000)))

        assertTrue(buckets.media.isEmpty())
    }

    @Test
    fun `a link row shows the host, not the whole URL`() {
        assertEquals("example.com", SharedContent.domainOf("https://www.example.com/a/b?c=d"))
        assertEquals("sub.example.co.uk", SharedContent.domainOf("http://sub.example.co.uk/x"))
        // Nothing host-shaped: the whole link beats an empty row.
        assertEquals("not a url", SharedContent.domainOf("not a url"))
    }

    @Test
    fun `an empty conversation reports empty for every tab`() {
        val buckets = SharedContent.partition(emptyList())

        SharedContent.Tab.entries.forEach { tab -> assertTrue(buckets.isEmpty(tab), tab.title) }
    }
}
