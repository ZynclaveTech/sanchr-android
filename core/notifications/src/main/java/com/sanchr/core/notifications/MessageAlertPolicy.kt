package com.sanchr.core.notifications

/**
 * What Settings → Notifications asks for, as far as a new message goes.
 *
 * Five switches on that screen were saved and sent to the server and read by
 * nothing on the phone. Notifications for messages are made on the phone —
 * the server never sees one — so turning any of them off changed nothing:
 * the phone went on alerting, with sound and vibration, for every message.
 */
data class MessageAlertSettings(
    /** The master switch. Off, no message is announced. */
    val enabled: Boolean = true,
    val directMessages: Boolean = true,
    val groupMessages: Boolean = true,
    val sound: Boolean = true,
    val vibrate: Boolean = true,
)

object MessageAlertPolicy {
    /** Whether a message in this kind of chat is announced at all. */
    fun shouldNotify(
        settings: MessageAlertSettings,
        isGroup: Boolean,
    ): Boolean = settings.enabled && if (isGroup) settings.groupMessages else settings.directMessages

    /**
     * The channel a message is posted on.
     *
     * Since Android 8 whether a notification makes a sound or vibrates is a
     * property of its channel, fixed when the channel is made; nothing set on
     * the notification itself overrides it. So there is a channel for each
     * combination, and the switches choose between them. The one for both is
     * the channel that has always existed, so anyone who has adjusted it in
     * the system's own settings keeps what they chose.
     */
    fun channelFor(settings: MessageAlertSettings): String =
        when {
            settings.sound && settings.vibrate -> NotificationHandler.CHANNEL_MESSAGES
            settings.sound -> NotificationHandler.CHANNEL_MESSAGES_SOUND_ONLY
            settings.vibrate -> NotificationHandler.CHANNEL_MESSAGES_VIBRATE_ONLY
            else -> NotificationHandler.CHANNEL_MESSAGES_QUIET
        }
}
