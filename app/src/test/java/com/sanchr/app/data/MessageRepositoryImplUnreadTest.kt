package com.sanchr.app.data

import com.sanchr.core.database.dao.BlockedUserDao
import com.sanchr.core.database.dao.ContactProfileDao
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.dao.PendingMessageAckDao
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.MessageStatus
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
            blockedUserDao = mockk<BlockedUserDao>(relaxed = true),
            contactProfileDao = mockk<ContactProfileDao>(relaxed = true),
            contactProfileResolver = mockk<ContactProfileResolver>(relaxed = true),
            sessionManager = mockk<SessionManager> { every { getUserId() } returns "self" },
            reactionDao = mockk(relaxed = true),
            notificationClient = notificationClient,
            autoVaultScheduler = mockk(relaxed = true),
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
            insert(sender = "peer")
            coVerify(exactly = 1) { conversationDao.incrementUnread("c1", 500L) }
        }

    @Test
    fun `a resend of an existing row and our own echoed message do not`() =
        runTest {
            coEvery { messageDao.insertMessageIfAbsent(match { it.id == "m1" }) } returns -1L
            insert(sender = "peer")
            insert(sender = "self", id = "m2")
            coVerify(exactly = 0) { conversationDao.incrementUnread(any(), any()) }
        }

    /**
     * The sender chooses a received message's id. Saving used to overwrite,
     * so reusing an id rewrote whatever message held it.
     */
    @Test
    fun `a received message is never saved over one already here`() =
        runTest {
            insert(sender = "peer")

            coVerify(exactly = 1) { messageDao.insertMessageIfAbsent(match { it.id == "m1" && it.senderId == "peer" }) }
            coVerify(exactly = 0) { messageDao.insertMessage(any()) }
            coVerify(exactly = 0) { messageDao.insertMessages(any()) }
        }

    @Test
    fun `only the two people in a one-to-one chat may have a message filed in it`() =
        runTest {
            coEvery { conversationDao.getConversationById("c1") } returns
                ConversationEntity(id = "c1", type = "DIRECT", participantIds = """["self","alice"]""", updatedAt = 1, createdAt = 1)
            coEvery { conversationDao.getConversationById("unknown") } returns null

            org.junit.Assert.assertTrue(repo.mayPostTo("c1", "alice"))
            org.junit.Assert.assertTrue(repo.mayPostTo("c1", "self"))
            org.junit.Assert.assertFalse(repo.mayPostTo("c1", "mallory"))
            // A chat this phone has not seen yet cannot be judged.
            org.junit.Assert.assertTrue(repo.mayPostTo("unknown", "mallory"))
        }

    private fun ownMessageInChatWithAlice() {
        coEvery { messageDao.getMessageById("m1") } returns
            MessageEntity(
                id = "m1",
                conversationId = "c1",
                senderId = "self",
                contentType = "text",
                contentBody = "hi",
                status = "SENT",
                timestamp = 1,
            )
        coEvery { conversationDao.getConversationById("c1") } returns
            ConversationEntity(id = "c1", type = "DIRECT", participantIds = """["self","alice"]""", updatedAt = 1, createdAt = 1)
        coEvery { messageDao.advanceOwnMessageStatus(any(), any(), any(), any()) } returns 1
    }

    @Test
    fun `a read receipt from the person the message went to is applied`() =
        runTest {
            ownMessageInChatWithAlice()

            org.junit.Assert.assertTrue(repo.applyReceiptStatus("m1", MessageStatus.READ, "alice"))

            coVerify(exactly = 1) {
                messageDao.advanceOwnMessageStatus("m1", "self", "READ", listOf("QUEUED", "SENDING", "SENT", "FAILED", "DELIVERED"))
            }
        }

    @Test
    fun `a receipt from someone outside the chat, from ourselves, or with a made-up status is not`() =
        runTest {
            ownMessageInChatWithAlice()
            coEvery { messageDao.getMessageById("not-here") } returns null

            org.junit.Assert.assertFalse(repo.applyReceiptStatus("m1", MessageStatus.READ, "mallory"))
            org.junit.Assert.assertFalse(repo.applyReceiptStatus("m1", MessageStatus.READ, "self"))
            // The outbox's cue to send it again.
            org.junit.Assert.assertFalse(repo.applyReceiptStatus("m1", MessageStatus.QUEUED, "alice"))
            org.junit.Assert.assertFalse(repo.applyReceiptStatus("m1", MessageStatus.FAILED, "alice"))
            org.junit.Assert.assertFalse(repo.applyReceiptStatus("not-here", MessageStatus.READ, "alice"))

            coVerify(exactly = 0) { messageDao.advanceOwnMessageStatus(any(), any(), any(), any()) }
            coVerify(exactly = 0) { messageDao.updateMessageStatus(any(), any()) }
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
