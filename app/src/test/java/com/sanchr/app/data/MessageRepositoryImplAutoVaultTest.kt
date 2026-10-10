package com.sanchr.app.data

import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.MessageContent
import com.sanchr.domain.messaging.vault.AutoVaultScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Which received messages are handed to Auto-Vault Incoming, and what it leaves in the chat. */
class MessageRepositoryImplAutoVaultTest {
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val conversationDao = mockk<ConversationDao>(relaxed = true)
    private val scheduler = mockk<AutoVaultScheduler>(relaxed = true)
    private val repo =
        MessageRepositoryImpl(
            messagingClient = mockk(relaxed = true),
            messageDao = messageDao,
            conversationDao = conversationDao,
            pendingMessageAckDao = mockk(relaxed = true),
            contactDao = mockk(relaxed = true),
            contactProfileDao = mockk(relaxed = true),
            contactProfileResolver = mockk(relaxed = true),
            sessionManager = mockk<SessionManager> { every { getUserId() } returns "self" },
            reactionDao = mockk(relaxed = true),
            notificationClient = mockk(relaxed = true),
            autoVaultScheduler = scheduler,
        )

    private fun conversation(autoVault: Boolean) =
        ConversationEntity(id = "c1", type = "DIRECT", participantIds = "[]", vaultAutoIncoming = autoVault, updatedAt = 1, createdAt = 1)

    private suspend fun receive(
        contentType: String,
        sender: String = "peer",
        alreadyStored: Boolean = false,
    ) {
        coEvery { messageDao.getMessageById("m1") } returns if (alreadyStored) mockk<MessageEntity>() else null
        repo.insertDecryptedMessage(
            "c1",
            "m1",
            sender,
            "{}",
            contentType,
            500L,
            flushAckImmediately = false,
            stageAck = false,
            expiresAtMillis = null,
            replyToId = null,
        )
    }

    @Test
    fun `received media in an auto-vault chat is scheduled`() =
        runTest {
            coEvery { conversationDao.getConversationById("c1") } returns conversation(autoVault = true)
            receive("image")
            verify(exactly = 1) { scheduler.schedule("m1") }
        }

    @Test
    fun `received media in an ordinary chat is not`() =
        runTest {
            coEvery { conversationDao.getConversationById("c1") } returns conversation(autoVault = false)
            receive("image")
            verify(exactly = 0) { scheduler.schedule(any()) }
        }

    @Test
    fun `text, our own echoes and redeliveries are not scheduled`() =
        runTest {
            coEvery { conversationDao.getConversationById("c1") } returns conversation(autoVault = true)
            receive("text")
            receive("image", sender = "self")
            // A redelivery replaces the row; scheduling again would vault the media twice.
            receive("image", alreadyStored = true)
            verify(exactly = 0) { scheduler.schedule(any()) }
        }

    @Test
    fun `the marker replaces the content and touches nothing off the device`() =
        runTest {
            repo.tombstoneAutoVaulted("m1")
            coVerify(exactly = 1) {
                messageDao.replaceContent("m1", MessageContent.System.CONTENT_TYPE, MessageContent.System.AUTO_VAULTED)
            }
        }
}
