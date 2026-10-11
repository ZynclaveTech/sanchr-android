package com.sanchr.core.notifications

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.datastore.NotifierState
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import io.mockk.MockKVerificationScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * The switches under Settings → Notifications, as the notifier applies them.
 * They used to be read by nothing on the phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewMessageNotifierAlertSettingsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
            override val unconfined: CoroutineDispatcher = dispatcher
            override val signalDispatcher: CoroutineDispatcher = dispatcher
        }
    private val messageDao = mockk<MessageDao>()
    private val conversationDao = mockk<ConversationDao>()
    private val notifierState = mockk<NotifierState>(relaxed = true) { coEvery { getLastNotifiedTimestamp() } returns 0L }
    private val handler = mockk<NotificationHandler>(relaxed = true)
    private val prefs =
        mockk<UserPreferences> {
            every { showPreviewOnLockscreen } returns flowOf(false)
            every { notificationPreview } returns flowOf(NotificationPreviewPolicy.ALWAYS)
        }

    private fun settings(
        enabled: Boolean = true,
        direct: Boolean = true,
        groups: Boolean = true,
        sound: Boolean = true,
        vibrate: Boolean = true,
    ) {
        every { prefs.notificationsEnabled } returns flowOf(enabled)
        every { prefs.messageNotificationsEnabled } returns flowOf(direct)
        every { prefs.groupNotificationsEnabled } returns flowOf(groups)
        every { prefs.notificationSoundEnabled } returns flowOf(sound)
        every { prefs.notificationVibrateEnabled } returns flowOf(vibrate)
    }

    private fun message(
        id: String,
        conversation: String,
    ) = MessageEntity(
        id = id,
        conversationId = conversation,
        senderId = "peer",
        contentType = "text",
        contentBody = "hi",
        status = "DELIVERED",
        timestamp = 1_000,
    )

    private fun chat(
        id: String,
        type: String,
        muted: Boolean = false,
    ) = mockk<ConversationEntity> {
        every { this@mockk.type } returns type
        every { isMuted } returns muted
    }.also { coEvery { conversationDao.getConversationById(id) } returns it }

    private fun TestScope.deliver(vararg messages: MessageEntity) {
        every { messageDao.observeIncomingMessagesAfter(any(), any()) } returns flowOf(messages.toList())
        NewMessageNotifier(
            messageDao = messageDao,
            conversationDao = conversationDao,
            contactDao = mockk(relaxed = true),
            sessionManager = mockk<SessionManager> { every { getUserId() } returns "self" },
            userPreferences = prefs,
            notifierState = notifierState,
            notificationHandler = handler,
            dispatchers = dispatchers,
            applicationScope = backgroundScope,
        ).start()
    }

    private fun MockKVerificationScope.announced(id: String) =
        handler.showMessageNotification(match { it.id == id }, any(), any(), any(), any())

    @Test
    fun `with everything on a message is announced on the ordinary channel`() =
        runTest(dispatcher) {
            settings()
            chat("c1", "DIRECT")

            deliver(message("m1", "c1"))

            verify(exactly = 1) {
                handler.showMessageNotification(match { it.id == "m1" }, any(), any(), any(), NotificationHandler.CHANNEL_MESSAGES)
            }
        }

    @Test
    fun `the master switch off announces nothing, and the message is still marked as seen`() =
        runTest(dispatcher) {
            settings(enabled = false)
            chat("c1", "DIRECT")

            deliver(message("m1", "c1"))

            verify(exactly = 0) { announced("m1") }
            // Or it would surface later, when the switch is turned back on.
            coVerify { notifierState.advanceLastNotifiedTimestamp(1_000) }
        }

    @Test
    fun `direct messages off still announces a group message, and the reverse`() =
        runTest(dispatcher) {
            settings(direct = false)
            chat("direct", "DIRECT")
            chat("group", "GROUP")

            deliver(message("m-direct", "direct"), message("m-group", "group"))

            verify(exactly = 0) { announced("m-direct") }
            verify(exactly = 1) { announced("m-group") }
        }

    @Test
    fun `group messages off still announces a direct message`() =
        runTest(dispatcher) {
            settings(groups = false)
            chat("direct", "DIRECT")
            chat("group", "GROUP")

            deliver(message("m-direct", "direct"), message("m-group", "group"))

            verify(exactly = 1) { announced("m-direct") }
            verify(exactly = 0) { announced("m-group") }
        }

    @Test
    fun `sound and vibration off post the message on the silent channel`() =
        runTest(dispatcher) {
            settings(sound = false, vibrate = false)
            chat("c1", "DIRECT")

            deliver(message("m1", "c1"))

            verify(exactly = 1) {
                handler.showMessageNotification(match { it.id == "m1" }, any(), any(), any(), NotificationHandler.CHANNEL_MESSAGES_QUIET)
            }
        }

    @Test
    fun `a muted chat stays silent whatever the switches say`() =
        runTest(dispatcher) {
            settings()
            chat("c1", "DIRECT", muted = true)

            deliver(message("m1", "c1"))

            verify(exactly = 0) { announced("m1") }
        }

    /** A chat the phone has no row for yet is treated as a direct one, and announced. */
    @Test
    fun `a message for a chat not yet stored is still announced`() =
        runTest(dispatcher) {
            settings()
            coEvery { conversationDao.getConversationById("new") } returns null

            deliver(message("m1", "new"))

            verify(exactly = 1) { announced("m1") }
        }
}
