package com.sanchr.core.notifications

/**
 * What a message notification is allowed to say, per Settings → Notifications
 * → Message Preview.
 *
 * The screen offers Always, Contacts only and Never, and until now the
 * notifier read none of it: every notification carried the sender's name and
 * the message text whatever the user chose. A privacy control has to actually
 * withhold something.
 *
 * Kept free of Android types so the decision is testable on its own; the
 * builder just renders what this returns.
 */
object NotificationPreviewPolicy {
    const val ALWAYS = "always"
    const val CONTACTS = "contacts"
    const val NEVER = "never"

    /** A title and body that are safe to show under the current setting. */
    data class Content(
        val title: String,
        val body: String,
    )

    /**
     * @param preview the stored setting; anything unrecognised is treated as
     *   [NEVER], so a value this build does not know cannot leak more than the
     *   strictest option would.
     * @param senderDisplayName the contact's name, or null when the sender is
     *   not in the address book. "Contacts only" turns on exactly this.
     * @param messagePreview one line describing the message, already reduced
     *   to a label for non-text content by the caller.
     */
    fun decide(
        preview: String,
        senderDisplayName: String?,
        messagePreview: String,
    ): Content {
        val named = senderDisplayName?.takeIf { it.isNotBlank() }
        return when {
            preview == ALWAYS -> Content(title = named ?: GENERIC_TITLE, body = messagePreview)
            // Not a contact under "Contacts only" is treated exactly like
            // "Never": showing the name of someone the user has not saved is
            // the leak this option exists to prevent.
            preview == CONTACTS && named != null -> Content(title = named, body = messagePreview)
            else -> Content(title = GENERIC_TITLE, body = GENERIC_BODY)
        }
    }

    private const val GENERIC_TITLE = "New message"
    private const val GENERIC_BODY = "Open Sanchr to read it"
}
