package com.sanchr.app.data

import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.dao.ContactProfileDao
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.dao.PendingMessageAckDao
import com.sanchr.core.database.entity.ContactProfileEntity
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.domain.messaging.ContactProfileResolver
import com.sanchr.proto.messaging.Conversation as ProtoConversation
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.Participant
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A DIRECT conversation is titled with what we call the peer, never with
 * the plaintext `display_name` the server attaches to participants.
 */
class MessageRepositoryImplConversationTitleTest {
    private val messagingClient = mockk<MessagingServiceClient>()
    private val conversationDao =
        mockk<ConversationDao>(relaxed = true) {
            // No local row: createConversation returns what it just wrote.
            coEvery { getConversationById(any()) } returns null
        }
    private val contactProfileDao = mockk<ContactProfileDao>()
    private val resolver = mockk<ContactProfileResolver>()
    private val sessionManager = mockk<SessionManager> { every { getUserId() } returns "self" }

    private val repo =
        MessageRepositoryImpl(
            messagingClient = messagingClient,
            messageDao = mockk<MessageDao>(relaxed = true),
            conversationDao = conversationDao,
            pendingMessageAckDao = mockk<PendingMessageAckDao>(relaxed = true),
            contactDao = mockk<ContactDao>(relaxed = true),
            contactProfileDao = contactProfileDao,
            contactProfileResolver = resolver,
            sessionManager = sessionManager,
        )

    private fun serverDirectConversation(serverPeerName: String) =
        ProtoConversation(
            id = "conv-1",
            type = "direct",
            participantIds = listOf("self", "peer"),
            participants =
                listOf(
                    Participant(userId = "self", displayName = "Me"),
                    Participant(userId = "peer", displayName = serverPeerName),
                ),
            title = "",
        )

    @Test
    fun `a direct conversation is titled by our resolution of the peer, not the server's plaintext`() =
        runTest {
            coEvery { messagingClient.startDirectConversation(any()) } returns serverDirectConversation("Sanchr User")
            coEvery { contactProfileDao.getByUserId("peer") } returns null
            coEvery { resolver.displayNameFor("peer") } returns "~Alice"
            val inserted = slot<List<ConversationEntity>>()

            val conversation = repo.createConversation("peer")

            coVerify { conversationDao.upsertFromServer(capture(inserted)) }
            assertEquals("~Alice", inserted.captured.single().title)
            assertEquals("~Alice", conversation.title)
        }

    @Test
    fun `the peer's decrypted avatar is preferred over the server's participant avatar`() =
        runTest {
            coEvery { messagingClient.startDirectConversation(any()) } returns serverDirectConversation("Alice")
            coEvery { contactProfileDao.getByUserId("peer") } returns
                ContactProfileEntity(userId = "peer", avatarUrl = "https://cdn/decrypted.jpg", updatedAt = 1L)
            coEvery { resolver.displayNameFor("peer") } returns "+15550001"
            val inserted = slot<List<ConversationEntity>>()

            repo.createConversation("peer")

            coVerify { conversationDao.upsertFromServer(capture(inserted)) }
            assertEquals("https://cdn/decrypted.jpg", inserted.captured.single().avatarUrl)
            assertEquals("+15550001", inserted.captured.single().title)
        }

    @Test
    fun `a group keeps the server's title`() =
        runTest {
            coEvery { messagingClient.startDirectConversation(any()) } returns
                ProtoConversation(id = "g", type = "group", participantIds = listOf("self", "a", "b"), title = "Weekend plans")
            val inserted = slot<List<ConversationEntity>>()

            repo.createConversation("a")

            coVerify { conversationDao.upsertFromServer(capture(inserted)) }
            assertEquals("Weekend plans", inserted.captured.single().title)
            coVerify(exactly = 0) { resolver.displayNameFor(any()) }
        }
}
