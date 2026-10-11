package com.sanchr.feature.chats

import com.sanchr.core.model.MediaAttachment
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When the long-press menu offers Save and Share. */
class SaveableMediaTest {
    private fun attachment(mimeType: String) = MediaAttachment(url = "sanchr-media://m", mimeType = mimeType, sizeBytes = 10)

    private fun message(
        contentType: String,
        mimeType: String? = null,
        viewOnce: Boolean = false,
        ephemeral: Boolean = false,
        albumOf: Int = 0,
    ) = MessageUiModel(
        id = "m1",
        text = "",
        timestamp = 1,
        isFromMe = false,
        status = MessageStatus.DELIVERED,
        contentType = contentType,
        attachment = mimeType?.let(::attachment),
        albumAttachments = (0 until albumOf).map { attachment("image/jpeg") },
        isViewOnce = viewOnce,
        isEphemeral = ephemeral,
    )

    @Test
    fun `a photo or a video can be saved and shared`() {
        assertTrue(message("image", "image/jpeg").isSaveableMedia)
        assertTrue(message("video", "video/mp4").isSaveableMedia)
        // A video that arrived typed as a file is still a video.
        assertTrue(message("file", "video/quicktime").isSaveableMedia)
    }

    @Test
    fun `text, voice notes and documents are not gallery media`() {
        assertFalse(message("text").isSaveableMedia)
        assertFalse(message("voice", "audio/mp4").isSaveableMedia)
        assertFalse(message("file", "application/pdf").isSaveableMedia)
    }

    /** Copying either out of the chat defeats what it was sent as. */
    @Test
    fun `view-once and disappearing media are not offered`() {
        assertFalse(message("image", "image/jpeg", viewOnce = true).isSaveableMedia)
        assertFalse(message("image", "image/jpeg", ephemeral = true).isSaveableMedia)
        assertFalse(message("video", "video/mp4", ephemeral = true).isSaveableMedia)
    }

    @Test
    fun `an album is saved picture by picture from the viewer, not from the menu`() {
        assertFalse(message("album", "image/jpeg", albumOf = 3).isSaveableMedia)
    }

    @Test
    fun `a photo whose attachment never arrived has nothing to save`() {
        assertFalse(message("image").isSaveableMedia)
    }
}
