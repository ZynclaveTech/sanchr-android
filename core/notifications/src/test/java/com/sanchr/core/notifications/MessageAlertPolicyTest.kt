package com.sanchr.core.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The notification switches were saved and read by nothing on the phone. */
class MessageAlertPolicyTest {
    private val all = MessageAlertSettings()

    @Test
    fun `with everything on, direct and group messages are both announced`() {
        assertTrue(MessageAlertPolicy.shouldNotify(all, isGroup = false))
        assertTrue(MessageAlertPolicy.shouldNotify(all, isGroup = true))
    }

    @Test
    fun `the master switch silences both`() {
        val off = all.copy(enabled = false)

        assertFalse(MessageAlertPolicy.shouldNotify(off, isGroup = false))
        assertFalse(MessageAlertPolicy.shouldNotify(off, isGroup = true))
    }

    @Test
    fun `direct messages off leaves groups alone, and the other way round`() {
        val noDirect = all.copy(directMessages = false)
        val noGroups = all.copy(groupMessages = false)

        assertFalse(MessageAlertPolicy.shouldNotify(noDirect, isGroup = false))
        assertTrue(MessageAlertPolicy.shouldNotify(noDirect, isGroup = true))
        assertTrue(MessageAlertPolicy.shouldNotify(noGroups, isGroup = false))
        assertFalse(MessageAlertPolicy.shouldNotify(noGroups, isGroup = true))
    }

    /** Sound and vibration belong to the channel, so each combination has its own. */
    @Test
    fun `sound and vibration choose the channel`() {
        assertEquals(NotificationHandler.CHANNEL_MESSAGES, MessageAlertPolicy.channelFor(all))
        assertEquals(NotificationHandler.CHANNEL_MESSAGES_SOUND_ONLY, MessageAlertPolicy.channelFor(all.copy(vibrate = false)))
        assertEquals(NotificationHandler.CHANNEL_MESSAGES_VIBRATE_ONLY, MessageAlertPolicy.channelFor(all.copy(sound = false)))
        assertEquals(NotificationHandler.CHANNEL_MESSAGES_QUIET, MessageAlertPolicy.channelFor(all.copy(sound = false, vibrate = false)))
    }

    @Test
    fun `the four channels are four different channels`() {
        val ids =
            setOf(
                NotificationHandler.CHANNEL_MESSAGES,
                NotificationHandler.CHANNEL_MESSAGES_SOUND_ONLY,
                NotificationHandler.CHANNEL_MESSAGES_VIBRATE_ONLY,
                NotificationHandler.CHANNEL_MESSAGES_QUIET,
            )

        assertEquals(4, ids.size)
    }

    /** Turning sound off must not be a way of turning the message off. */
    @Test
    fun `a quieter channel is still an announcement`() {
        assertTrue(MessageAlertPolicy.shouldNotify(all.copy(sound = false, vibrate = false), isGroup = false))
    }
}
