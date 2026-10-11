package com.sanchr.core.callengine

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A call notification has to open the call screen. Its tap and its
 * full-screen intent were a broadcast nobody received, so an incoming call
 * had no screen at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class CallNotificationManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val system = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val manager = CallNotificationManager(context)

    private fun incoming(): Notification = manager.showIncomingCallNotification("Alice", isVideo = false, callId = "call-1")

    private fun Notification.action(title: String): PendingIntent = actions.single { it.title == title }.actionIntent

    private fun assertOpensCallScreen(
        intent: PendingIntent?,
        callId: String,
        answers: Boolean,
    ) {
        val shadow = shadowOf(assertNotNull(intent))
        assertTrue(shadow.isActivityIntent, "it must start a screen, not send a broadcast")
        val saved = shadow.savedIntent
        assertEquals(CallNotificationManager.CALL_ACTIVITY_CLASS, saved.component?.className)
        assertEquals(context.packageName, saved.component?.packageName)
        assertEquals(callId, saved.getStringExtra(CallNotificationManager.EXTRA_CALL_ID))
        assertEquals(
            if (answers) CallNotificationManager.CALL_ACTION_ANSWER else null,
            saved.getStringExtra(CallNotificationManager.EXTRA_CALL_ACTION),
        )
        assertTrue(shadow.flags and PendingIntent.FLAG_IMMUTABLE != 0)
    }

    @Test
    fun `an incoming call shows the call screen full-screen and on a tap`() {
        val notification = incoming()

        assertOpensCallScreen(notification.fullScreenIntent, "call-1", answers = false)
        assertOpensCallScreen(notification.contentIntent, "call-1", answers = false)
    }

    @Test
    fun `Answer opens the call screen and asks it to answer`() {
        assertOpensCallScreen(incoming().action("Answer"), "call-1", answers = true)
    }

    /** Opening the screen and answering must not be the same pending intent, or one overwrites the other. */
    @Test
    fun `opening and answering are told apart`() {
        val notification = incoming()

        val opens = shadowOf(notification.contentIntent).savedIntent
        val answers = shadowOf(notification.action("Answer")).savedIntent

        assertNull(opens.getStringExtra(CallNotificationManager.EXTRA_CALL_ACTION))
        assertEquals(CallNotificationManager.CALL_ACTION_ANSWER, answers.getStringExtra(CallNotificationManager.EXTRA_CALL_ACTION))
    }

    @Test
    fun `Decline still goes to the call service, which needs no screen`() {
        val shadow = shadowOf(incoming().action("Decline"))

        assertTrue(shadow.isService)
        assertEquals(CallService.ACTION_DECLINE, shadow.savedIntent.action)
    }

    @Test
    fun `the notification for a call under way opens the call screen too`() {
        val ongoing = manager.showOngoingCallNotification("Alice", callType = "voice", isIncoming = false)

        assertOpensCallScreen(ongoing.contentIntent, "", answers = false)
    }

    /**
     * Left up for the whole call, it went on offering Decline — which,
     * pressed mid-call, stopped the call service under a call that carried on.
     */
    @Test
    fun `the ringing notification comes down once the call is answered`() {
        incoming()
        assertNotNull(shadowOf(system).getNotification(CallNotificationManager.NOTIFICATION_ID_INCOMING))

        manager.cancelIncomingCallNotification()

        assertNull(shadowOf(system).getNotification(CallNotificationManager.NOTIFICATION_ID_INCOMING))
    }
}
