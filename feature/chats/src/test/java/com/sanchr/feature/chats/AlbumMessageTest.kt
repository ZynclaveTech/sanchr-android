package com.sanchr.feature.chats

import com.sanchr.core.model.MediaAttachment
import com.sanchr.feature.chats.media.GallerySelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Albums: several photos or videos sent as one message.
 *
 * The receive path decoded `attachments.first()` and dropped the rest, so an
 * album of four from iOS arrived as one photo and the other three were
 * unreachable — the bytes were in the message and nothing could show them.
 */
class AlbumMessageTest {
    private fun attachment(url: String) =
        MediaAttachment(
            url = url,
            mimeType = "image/jpeg",
            sizeBytes = 1_024,
        )

    private fun album(
        id: String = "msg-1",
        count: Int = 3,
        timestamp: Long = 1_000L,
    ) = MessageUiModel(
        id = id,
        text = "[3 photos]",
        timestamp = timestamp,
        isFromMe = false,
        status = MessageStatus.DELIVERED,
        contentType = "album",
        attachment = attachment("https://media.example/0.jpg"),
        albumAttachments = (0 until count).map { attachment("https://media.example/$it.jpg") },
    )

    @Test
    fun `a tile is a row of its own, keyed by its index`() {
        val tile = assertNotNull(album().albumTile(2))

        // Keyed by index because the download cache is keyed by row id:
        // sharing the message's id would make every tile collide on one
        // cached file and render the same photo.
        assertEquals("msg-1#2", tile.id)
        assertEquals("https://media.example/2.jpg", tile.attachment?.url)
        assertTrue(tile.albumAttachments.isEmpty())
    }

    @Test
    fun `a tile past the end is absent rather than a crash`() {
        assertNull(album(count = 2).albumTile(5))
    }

    @Test
    fun `the gallery holds every tile, and opens on the one that was tapped`() {
        val state = assertNotNull(GallerySelection.from(listOf(album()), tappedId = "msg-1#1"))

        assertEquals(listOf("msg-1#0", "msg-1#1", "msg-1#2"), state.items.map { it.id })
        assertEquals(1, state.initialIndex)
    }

    @Test
    fun `albums and single photos sit together in arrival order`() {
        val single =
            MessageUiModel(
                id = "single",
                text = "",
                timestamp = 2_000L,
                isFromMe = false,
                status = MessageStatus.DELIVERED,
                contentType = "image",
                attachment = attachment("https://media.example/single.jpg"),
            )
        val state = assertNotNull(GallerySelection.from(listOf(single, album(count = 2)), tappedId = "single"))

        assertEquals(listOf("msg-1#0", "msg-1#1", "single"), state.items.map { it.id })
        assertEquals(2, state.initialIndex)
    }

    @Test
    fun `a single-attachment message is not an album`() {
        val single = album(count = 1)

        assertTrue(!single.isAlbum)
    }
}
