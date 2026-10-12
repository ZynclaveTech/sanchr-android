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
import com.sanchr.core.notifications.NotificationHandler
import com.sanchr.domain.messaging.ContactProfileResolver
import com.sanchr.domain.messaging.MessageMediaEvictor
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
    private val messageDao =
        mockk<MessageDao>(relaxed = true) {
            // A relaxed mock answers false: every message "already here".
            coEvery { insertReceivedMessage(any()) } returns true
        }
    private val conversationDao = mockk<ConversationDao>(relaxed = true)
    private val messagingClient = mockk<MessagingServiceClient>(relaxed = true)
    private val notificationClient = mockk<NotificationServiceClient>(relaxed = true)
    private val notificationHandler = mockk<NotificationHandler>(relaxed = true)
    private val userPreferences = mockk<com.sanchr.core.datastore.UserPreferences>(relaxed = true)
    private val mediaEvictor = mockk<MessageMediaEvictor>(relaxed = true)
    private val repo =
        MessageRepositoryImpl(
            messagingClient = messagingClient,
            messageDao = messageDao,
            conversationDao = conversationDao,
            pendingMessageAckDao = mockk<PendingMessageAckDao>(relaxed = true),
            blockedUserDao = mockk<BlockedUserDao>(relaxed = true),
            notificationHandler = notificationHandler,
            userPreferences = userPreferences,
            mediaEvictor = { mediaEvictor },
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
            coEvery { messageDao.insertReceivedMessage(match { it.id == "m1" }) } returns false
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

            coVerify(exactly = 1) { messageDao.insertReceivedMessage(match { it.id == "m1" && it.senderId == "peer" }) }
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

    private val photo = MessageMediaEvictor.CachedFile("m1", "image/jpeg")

    @Test
    fun `deleting a message removes its cached media, before the row is emptied and again after`() =
        runTest {
            coEvery { mediaEvictor.evict("m1") } returns listOf(photo)

            repo.deleteMessage("m1", forEveryone = false)

            coVerifyOrder {
                mediaEvictor.evict("m1")
                messageDao.softDeleteMessage("m1")
                mediaEvictor.again(listOf(photo))
            }
        }

    @Test
    fun `deleting a conversation removes the cached media of every message in it`() =
        runTest {
            coEvery { messageDao.messageIdsInConversation("c1") } returns listOf("m1", "m2")
            coEvery { mediaEvictor.evict("m1") } returns listOf(photo)
            coEvery { mediaEvictor.evict("m2") } returns emptyList()

            repo.deleteConversation("c1")

            coVerifyOrder {
                mediaEvictor.evict("m1")
                mediaEvictor.evict("m2")
                conversationDao.deleteConversation("c1")
                mediaEvictor.again(listOf(photo))
            }
        }

    @Test
    fun `messages deleted by an older version are emptied, media first`() =
        runTest {
            coEvery { messageDao.deletedMessageIdsStillHoldingContent() } returns listOf("m1")
            coEvery { mediaEvictor.evict("m1") } returns listOf(photo)
            coEvery { messageDao.emptyDeletedMessages() } returns 1

            org.junit.Assert.assertEquals(1, repo.eraseDeletedMessages())

            coVerifyOrder {
                mediaEvictor.evict("m1")
                messageDao.emptyDeletedMessages()
                mediaEvictor.again(listOf(photo))
            }
        }

    @Test
    fun `with nothing left over, erasing touches nothing`() =
        runTest {
            coEvery { messageDao.deletedMessageIdsStillHoldingContent() } returns emptyList()

            org.junit.Assert.assertEquals(0, repo.eraseDeletedMessages())

            coVerify(exactly = 0) { messageDao.emptyDeletedMessages() }
        }

    private fun incoming(sender: String) =
        MessageEntity(
            id = "m1",
            conversationId = "c1",
            senderId = sender,
            contentType = "text",
            contentBody = "hi",
            status = "DELIVERED",
            timestamp = 1,
        )

    /** A notification goes on showing what a message said after the message has gone. */
    @Test
    fun `clearing, deleting or reading a chat withdraws its notification`() =
        runTest {
            repo.clearMessages("c1")
            repo.deleteConversation("c2")
            repo.markAsRead("c3")

            coVerify(exactly = 1) { notificationHandler.cancelNotificationsForConversation("c1") }
            coVerify(exactly = 1) { notificationHandler.cancelNotificationsForConversation("c2") }
            coVerify(exactly = 1) { notificationHandler.cancelNotificationsForConversation("c3") }
        }

    @Test
    fun `deleting a message someone sent withdraws the chat's notification, deleting our own does not`() =
        runTest {
            coEvery { messageDao.getMessageById("m1") } returns incoming("peer")
            repo.deleteMessage("m1", forEveryone = false)
            coVerify(exactly = 1) { notificationHandler.cancelNotificationsForConversation("c1") }

            coEvery { messageDao.getMessageById("m1") } returns incoming("self")
            repo.deleteMessage("m1", forEveryone = false)
            coVerify(exactly = 1) { notificationHandler.cancelNotificationsForConversation("c1") }
        }

    @Test
    fun `an expired message takes its chat's notification with it`() =
        runTest {
            coEvery { messageDao.conversationsWithUnreadExpiredMessages(500L, "self") } returns listOf("c1")
            coEvery { messageDao.deleteExpiredMessages(500L) } returns 2

            org.junit.Assert.assertEquals(2, repo.deleteExpiredMessages(500L))

            coVerifyOrder {
                messageDao.conversationsWithUnreadExpiredMessages(500L, "self")
                messageDao.deleteExpiredMessages(500L)
                notificationHandler.cancelNotificationsForConversation("c1")
            }
            coVerify(exactly = 0) { notificationHandler.cancelNotificationsForConversation("c2") }
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
