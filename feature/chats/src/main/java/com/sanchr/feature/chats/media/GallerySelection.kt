package com.sanchr.feature.chats.media

import com.sanchr.feature.chats.MessageUiModel

/**
 * Which messages the full-screen gallery pages through, and where it opens.
 *
 * Photos and videos from the whole loaded transcript, oldest first, so
 * swiping sideways walks the conversation's media in the order it arrived —
 * the same set iOS's `MediaGalleryCoordinator` builds.
 */
object GallerySelection {
    /**
     * The gallery for [messages] opened at [tappedId], or null when that
     * message has no media to show.
     *
     * View-once media is left out. It has its own consuming viewer, and
     * letting an ordinary swipe land on it would burn it without the user
     * ever choosing to open it.
     */
    fun from(
        messages: List<MessageUiModel>,
        tappedId: String,
    ): GalleryState? {
        val items = messages.filter { it.isGalleryMedia }.sortedBy { it.timestamp }
        val index = items.indexOfFirst { it.id == tappedId }
        if (index < 0) return null
        return GalleryState(items = items, initialIndex = index)
    }

    private val MessageUiModel.isGalleryMedia: Boolean
        get() = attachment != null && !isViewOnce && (contentType == "image" || isVideoAttachment)
}

/** The media the gallery is showing, and which one is on screen first. */
data class GalleryState(
    val items: List<MessageUiModel>,
    val initialIndex: Int,
)
