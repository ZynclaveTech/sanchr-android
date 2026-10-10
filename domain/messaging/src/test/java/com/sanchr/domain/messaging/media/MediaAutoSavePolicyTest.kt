package com.sanchr.domain.messaging.media

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaAutoSavePolicyTest {
    private fun shouldSave(
        isViewOnce: Boolean = false,
        isEphemeral: Boolean = false,
        autoSaveEnabled: Boolean = true,
        galleryVisible: Boolean = true,
        isOutgoing: Boolean = false,
        mimeType: String = "image/jpeg",
    ) = MediaAutoSavePolicy.shouldAutoSave(
        isViewOnce = isViewOnce,
        isEphemeral = isEphemeral,
        autoSaveEnabled = autoSaveEnabled,
        galleryVisible = galleryVisible,
        isOutgoing = isOutgoing,
        mimeType = mimeType,
    )

    @Test
    fun `saves incoming media when the setting is on and the chat is visible`() {
        assertTrue(shouldSave())
        assertTrue(shouldSave(mimeType = "video/mp4"))
    }

    @Test
    fun `never saves view-once media`() {
        assertFalse(shouldSave(isViewOnce = true))
    }

    @Test
    fun `never saves media on a disappearing timer`() {
        assertFalse(shouldSave(isEphemeral = true))
    }

    @Test
    fun `does not save when the setting is off`() {
        assertFalse(shouldSave(autoSaveEnabled = false))
    }

    @Test
    fun `does not save a chat excluded from the gallery`() {
        assertFalse(shouldSave(galleryVisible = false))
    }

    @Test
    fun `does not save our own outgoing media`() {
        assertFalse(shouldSave(isOutgoing = true))
    }

    @Test
    fun `only images and video can be saved`() {
        assertFalse(shouldSave(mimeType = "application/pdf"))
        assertFalse(shouldSave(mimeType = "audio/ogg"))
        assertTrue(MediaAutoSavePolicy.isSaveableType("image/png"))
        assertTrue(MediaAutoSavePolicy.isSaveableType("video/quicktime"))
        assertFalse(MediaAutoSavePolicy.isSaveableType("text/plain"))
    }

    @Test
    fun `video is distinguished so the right gallery collection is used`() {
        assertTrue(MediaAutoSavePolicy.isVideo("video/mp4"))
        assertFalse(MediaAutoSavePolicy.isVideo("image/jpeg"))
    }
}
