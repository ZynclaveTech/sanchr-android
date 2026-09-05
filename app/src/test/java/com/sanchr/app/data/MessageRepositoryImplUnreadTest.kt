package com.sanchr.app.data

import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.dao.ContactProfileDao
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.dao.PendingMessageAckDao
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.domain.messaging.ContactProfileResolver
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.notifications.NotificationServiceClient
import com.sanchr.proto.notifications.SetConversationNotificationPrefsRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** A new inbound row bumps the chat's unread count and moves it up; resends and our own echoes do not. */
class MessageRepositoryImplUnreadTest {
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val conversationDao = mockk<ConversationDao>(relaxed = true)
    private val messagingClient = mockk<MessagingServiceClient>(relaxed = true)
    private val notificationClient = mockk<NotificationServiceClient>(relaxed = true)
    private val repo =
        MessageRepositoryImpl(
            messagingClient = messagingClient,
            messageDao = messageDao,
            conversationDao = conversationDao,
            pendingMessageAckDao = mockk<PendingMessageAckDao>(relaxed = true),
            contactDao = mockk<ContactDao>(relaxed = true),
            contactProfileDao = mockk<ContactProfileDao>(relaxed = true),
            contactProfileResolver = mockk<ContactProfileResolver>(relaxed = true),
            sessionManager = mockk<SessionManager> { every { getUserId() } returns "self" },
            reactionDao = mockk(relaxed = true),
            notificationClient = notificationClient,
        )

    private suspend fun insert(
        sender: String,
        id: String = "m1",
    ) = repo.insertDecryptedMessage(
        "c1",
        id,
        sender,
        "hi",
        "text",
        500L,
        flushAckImmediately = false,
        stageAck = false,
        expiresAtMillis = null,
        replyToId = null,
    )

    @Test
    fun `a peer's new message increments unread with its timestamp`() =
        runTest {
            coEvery { messageDao.getMessageById("m1") } returns null
            insert(sender = "peer")
            coVerify(exactly = 1) { conversationDao.incrementUnread("c1", 500L) }
        }

    @Test
    fun `a resend of an existing row and our own echoed message do not`() =
        runTest {
            coEvery { messageDao.getMessageById("m1") } returns mockk<MessageEntity>()
            insert(sender = "peer")
            coEvery { messageDao.getMessageById("m2") } returns null
            insert(sender = "self", id = "m2")
            coVerify(exactly = 0) { conversationDao.incrementUnread(any(), any()) }
        }

    @Test
    fun `search escapes LIKE wildcards and skips blank queries`() =
        runTest {
            coEvery { messageDao.searchTextMessageIds("c1", "100\\%\\_sure") } returns listOf("m1")
            org.junit.Assert.assertEquals(listOf("m1"), repo.searchMessages("c1", " 100%_sure "))
            org.junit.Assert.assertEquals(emptyList<String>(), repo.searchMessages("c1", "   "))
            coVerify(exactly = 1) { messageDao.searchTextMessageIds(any(), any()) }
        }

    @Test
    fun `deleting a conversation removes it locally even when the server refuses, and mute tells the server first`() =
        runTest {
            coEvery { messagingClient.deleteConversation(any()) } throws IOException("offline")
            repo.deleteConversation("c1")
            coVerify { conversationDao.deleteConversation("c1") }

            repo.setMuted("c1", true)
            coVerifyOrder {
                notificationClient.setConversationNotificationPrefs(SetConversationNotificationPrefsRequest("c1", true))
                conversationDao.setMuted("c1", true)
            }
        }
}
