package com.sanchr.domain.messaging.media

/**
 * Whether received media should be copied into the system gallery, per
 * Settings → Chats → Media Auto-Save.
 *
 * A port of iOS `MediaAutoSavePolicy`. Kept out of the UI so the decision is
 * testable on its own: it writes outside the app's own storage — for most
 * users, a backed-up photo library — so getting it wrong is not recoverable by
 * deleting the message afterwards.
 */
object MediaAutoSavePolicy {
    /**
     * @param isViewOnce whether the sender marked the media view-once.
     * @param autoSaveEnabled the Media Auto-Save switch.
     * @param galleryVisible the per-conversation Media Visibility switch.
     * @param isOutgoing whether this device sent the media.
     * @param mimeType the attachment's type; only images and video can be saved.
     */
    fun shouldAutoSave(
        isViewOnce: Boolean,
        autoSaveEnabled: Boolean,
        galleryVisible: Boolean,
        isOutgoing: Boolean,
        mimeType: String,
    ): Boolean {
        // View-once media is excluded unconditionally. Saving it would keep
        // permanently what the sender chose to show once, and deleting the
        // message later does not reach a photo library that has already synced.
        if (isViewOnce) return false
        if (!isSaveableType(mimeType)) return false
        return autoSaveEnabled && galleryVisible && !isOutgoing
    }

    /** Whether the gallery has a collection for this type at all. */
    fun isSaveableType(mimeType: String): Boolean = mimeType.startsWith("image/") || mimeType.startsWith("video/")

    fun isVideo(mimeType: String): Boolean = mimeType.startsWith("video/")
}
