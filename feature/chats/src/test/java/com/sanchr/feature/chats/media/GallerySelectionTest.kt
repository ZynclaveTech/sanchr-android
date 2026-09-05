package com.sanchr.feature.chats.media

import com.sanchr.core.model.MediaAttachment
import com.sanchr.feature.chats.MessageStatus
import com.sanchr.feature.chats.MessageUiModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GallerySelectionTest {
    private fun message(
        id: String,
        timestamp: Long,
        contentType: String,
        mimeType: String? = "image/jpeg",
        isViewOnce: Boolean = false,
    ) = MessageUiModel(
        id = id,
        text = "",
        timestamp = timestamp,
        isFromMe = false,
        status = MessageStatus.DELIVERED,
        contentType = contentType,
        attachment = mimeType?.let { MediaAttachment(url = "u", mimeType = it, sizeBytes = 1) },
        isViewOnce = isViewOnce,
    )

    @Test
    fun `pages photos and videos oldest first and opens on the tapped one`() {
        val messages =
            listOf(
                message("later-video", 300, "document", "video/mp4"),
                message("text", 200, "text", mimeType = null),
                message("early-photo", 100, "image"),
            )

        val state = GallerySelection.from(messages, tappedId = "later-video")

        assertEquals(listOf("early-photo", "later-video"), state?.items?.map { it.id })
        assertEquals(1, state?.initialIndex)
    }

    @Test
    fun `view-once media never joins the gallery`() {
        val messages =
            listOf(
                message("photo", 100, "image"),
                message("secret", 200, "image", isViewOnce = true),
            )

        assertEquals(listOf("photo"), GallerySelection.from(messages, "photo")?.items?.map { it.id })
        assertNull(GallerySelection.from(messages, "secret"), "a view-once tap must not open the swipeable gallery")
    }

    @Test
    fun `a message with no media opens nothing`() {
        assertNull(GallerySelection.from(listOf(message("t", 1, "text", mimeType = null)), "t"))
    }

    @Test
    fun `an id outside the transcript opens nothing`() {
        assertNull(GallerySelection.from(listOf(message("photo", 1, "image")), "missing"))
    }
}
