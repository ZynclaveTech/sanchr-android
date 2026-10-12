package com.sanchr.app.data

import com.sanchr.core.database.dao.BlockedUserDao
import com.sanchr.core.database.dao.ContactProfileDao
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.dao.PendingMessageAckDao
import com.sanchr.core.database.entity.ContactProfileEntity
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.domain.messaging.ContactProfileResolver
import com.sanchr.proto.messaging.Conversation as ProtoConversation
import com.sanchr.proto.messaging.GetConversationsResponse
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

    private val notificationHandler = mockk<com.sanchr.core.notifications.NotificationHandler>(relaxed = true)
    private val mediaEvictor = mockk<com.sanchr.domain.messaging.MessageMediaEvictor>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val repo =
        MessageRepositoryImpl(
            messagingClient = messagingClient,
            messageDao = messageDao,
            conversationDao = conversationDao,
            pendingMessageAckDao = mockk<PendingMessageAckDao>(relaxed = true),
            blockedUserDao = mockk<BlockedUserDao>(relaxed = true),
            notificationHandler = notificationHandler,
            mediaEvictor = { mediaEvictor },
            contactProfileDao = contactProfileDao,
            contactProfileResolver = resolver,
            sessionManager = sessionManager,
            reactionDao = mockk(relaxed = true),
            notificationClient = mockk(relaxed = true),
            autoVaultScheduler = mockk(relaxed = true),
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

    // ── A message for a chat this phone does not have yet ────────────────

    private suspend fun prepare(
        sender: String = "peer",
        messageId: String = "m1",
    ) = repo.prepareConversationForIncoming("conv-1", messageId, sender, 500L)

    /**
     * A message cannot be stored without its conversation, and nothing put
     * the conversation there: the first message from someone new was lost.
     */
    @Test
    fun `the server's copy of an unknown conversation is taken before its first message is saved`() =
        runTest {
            coEvery { messagingClient.getConversations(any()) } returns
                GetConversationsResponse(conversations = listOf(serverDirectConversation("Sanchr User")))
            coEvery { contactProfileDao.getByUserId("peer") } returns null
            coEvery { resolver.displayNameFor("peer") } returns "~Alice"
            val saved = slot<List<ConversationEntity>>()

            prepare()

            coVerify(exactly = 1) { conversationDao.upsertFromServer(capture(saved)) }
            assertEquals("conv-1", saved.captured.single().id)
            assertEquals("DIRECT", saved.captured.single().type)
            assertEquals("""["self","peer"]""", saved.captured.single().participantIds)
            assertEquals("~Alice", saved.captured.single().title)
        }

    @Test
    fun `offline, a placeholder with this user and the sender stands in until the next sync`() =
        runTest {
            coEvery { messagingClient.getConversations(any()) } throws java.io.IOException("offline")
            coEvery { resolver.displayNameFor("peer") } returns "+15550001"
            val saved = slot<List<ConversationEntity>>()

            prepare()

            coVerify(exactly = 1) { conversationDao.upsertFromServer(capture(saved)) }
            val shell = saved.captured.single()
            assertEquals("conv-1", shell.id)
            assertEquals("DIRECT", shell.type)
            assertEquals("""["self","peer"]""", shell.participantIds)
            assertEquals("+15550001", shell.title)
            assertEquals(500L, shell.updatedAt)
        }

    @Test
    fun `a conversation the server does not list for us still gets a placeholder`() =
        runTest {
            coEvery { messagingClient.getConversations(any()) } returns GetConversationsResponse(conversations = emptyList())
            coEvery { resolver.displayNameFor("peer") } returns "+15550001"
            val saved = slot<List<ConversationEntity>>()

            prepare()

            coVerify(exactly = 1) { conversationDao.upsertFromServer(capture(saved)) }
            assertEquals("""["self","peer"]""", saved.captured.single().participantIds)
        }

    @Test
    fun `our own message from another device makes a placeholder with just us in it`() =
        runTest {
            coEvery { messagingClient.getConversations(any()) } throws java.io.IOException("offline")
            val saved = slot<List<ConversationEntity>>()

            prepare(sender = "self")

            coVerify(exactly = 1) { conversationDao.upsertFromServer(capture(saved)) }
            assertEquals("""["self"]""", saved.captured.single().participantIds)
            assertEquals(null, saved.captured.single().title)
        }

    @Test
    fun `a conversation already here is left as it is, and the server is not asked`() =
        runTest {
            coEvery { conversationDao.getConversationById("conv-1") } returns
                ConversationEntity(id = "conv-1", type = "DIRECT", participantIds = "[]", updatedAt = 1, createdAt = 1)

            prepare()

            coVerify(exactly = 0) { messagingClient.getConversations(any()) }
            coVerify(exactly = 0) { conversationDao.upsertFromServer(any()) }
        }

    /** It is about to be turned away; its chat must not come back to hold nothing. */
    @Test
    fun `a message deleted from this phone does not bring its conversation back`() =
        runTest {
            coEvery { messageDao.wasDeleted("gone") } returns true

            prepare(messageId = "gone")

            coVerify(exactly = 0) { messagingClient.getConversations(any()) }
            coVerify(exactly = 0) { conversationDao.upsertFromServer(any()) }
        }
}
