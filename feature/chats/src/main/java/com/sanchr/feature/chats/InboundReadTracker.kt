package com.sanchr.feature.chats

/**
 * Decides when a message that arrives in an open chat counts as read.
 *
 * "Read" has to mean seen. The chat's view model outlives the screen being
 * in front — the app can be in the background, or the phone locked, with the
 * chat still open underneath — and its message stream keeps running the whole
 * time. So what arrives is read only while the screen is in front, and
 * anything that arrived meanwhile is read when it comes back.
 *
 * The first message it is shown is not its to announce: opening the chat
 * already reads everything in it.
 *
 * Not thread-safe; called from the view model's main-thread collectors.
 */
internal class InboundReadTracker(
    private val onRead: (messageId: String) -> Unit,
) {
    private var newestSeen: String? = null
    private var visible = false
    private var arrivedWhileHidden = false

    /** Called as the screen comes to the front and as it leaves it. */
    fun onVisibilityChanged(visible: Boolean) {
        this.visible = visible
        if (visible && arrivedWhileHidden) {
            arrivedWhileHidden = false
            newestSeen?.let(onRead)
        }
    }

    /** Called with the newest message from the other side, each time the list changes. */
    fun onNewestInbound(messageId: String?) {
        if (messageId == null || messageId == newestSeen) return
        val first = newestSeen == null
        newestSeen = messageId
        if (first) return
        if (visible) onRead(messageId) else arrivedWhileHidden = true
    }
}
