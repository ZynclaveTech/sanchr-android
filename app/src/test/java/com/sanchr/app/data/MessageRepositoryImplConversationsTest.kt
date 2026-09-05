package com.sanchr.app.data

import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.dao.ContactProfileDao
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.dao.PendingMessageAckDao
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.MessageContent
import com.sanchr.domain.messaging.ContactProfileResolver
import com.sanchr.proto.messaging.MessagingServiceClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The chat list gets each conversation's newest message and is ordered by it, pinned chats first. */
class MessageRepositoryImplConversationsTest {
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val conversationDao = mockk<ConversationDao>(relaxed = true)
    private val repo =
        MessageRepositoryImpl(
            messagingClient = mockk<MessagingServiceClient>(),
            messageDao = messageDao,
            conversationDao = conversationDao,
            pendingMessageAckDao = mockk<PendingMessageAckDao>(relaxed = true),
            contactDao = mockk<ContactDao>(relaxed = true),
            contactProfileDao = mockk<ContactProfileDao>(relaxed = true),
            contactProfileResolver = mockk<ContactProfileResolver>(relaxed = true),
            sessionManager = mockk<SessionManager> { every { getUserId() } returns "self" },
            reactionDao = mockk(relaxed = true),
            notificationClient = mockk(relaxed = true),
        )

    private fun conversation(
        id: String,
        updatedAt: Long,
        pinned: Boolean = false,
    ) = ConversationEntity(
        id = id,
        type = "DIRECT",
        title = id,
        participantIds = """["self","peer"]""",
        isPinned = pinned,
        updatedAt = updatedAt,
        createdAt = 1,
    )

    private fun message(
        id: String,
        conversationId: String,
        timestamp: Long,
        body: String,
    ) = MessageEntity(
        id = id,
        conversationId = conversationId,
        senderId = "peer",
        contentType = "text",
        contentBody = body,
        status = "DELIVERED",
        timestamp = timestamp,
    )

    @Test
    fun `newest messages are attached and chats are ordered pinned first then by that message`() =
        runTest {
            every { conversationDao.observeConversations() } returns
                flowOf(
                    listOf(
                        conversation("a", updatedAt = 10),
                        conversation("b", updatedAt = 20),
                        conversation("c", updatedAt = 5, pinned = true),
                        conversation("d", updatedAt = 30),
                    ),
                )
            every { messageDao.observeLatestPerConversation() } returns
                flowOf(listOf(message("m1", "a", 100, "newest in a"), message("m2", "b", 50, "b"), message("m3", "b", 50, "b-tie")))

            val list = repo.observeConversations().first()

            assertEquals(listOf("c", "a", "b", "d"), list.map { it.id })
            assertEquals("newest in a", (list[1].lastMessage!!.content as MessageContent.Text).body)
            assertNull(list[0].lastMessage)
            assertNull(list[3].lastMessage)
        }
}
