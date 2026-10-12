package com.sanchr.app.data

import com.sanchr.core.database.dao.BlockedUserDao
import com.sanchr.core.database.dao.ContactProfileDao
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.dao.MessageSender
import com.sanchr.core.database.dao.PendingMessageAckDao
import com.sanchr.core.database.entity.ContactProfileEntity
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
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
import kotlinx.coroutines.flow.flowOf
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
    private val userPreferences = mockk<UserPreferences>(relaxed = true)
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
            userPreferences = userPreferences,
            heldIncomingStore = mockk(relaxed = true),
            placeholders =
                PlaceholderConversations(
                    userPreferences = userPreferences,
                    conversationDao = conversationDao,
                    messageDao = messageDao,
                    sessionManager = sessionManager,
                    contactProfileResolver = resolver,
                    notificationHandler = notificationHandler,
                    mediaEvictor = { mediaEvictor },
                ),
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
            // The server's own copy is not a guess.
            coVerify(exactly = 0) { userPreferences.addPlaceholderConversation(any()) }
        }

    @Test
    fun `offline, a placeholder with this user and the sender stands in until the next sync`() =
        runTest {
            coEvery { messagingClient.getConversations(any()) } throws java.io.IOException("offline")
            coEvery { resolver.displayNameFor("peer") } returns "+15550001"
            val saved = slot<List<ConversationEntity>>()

            prepare()

            coVerify(exactly = 1) { conversationDao.upsertFromServer(capture(saved)) }
            // Remembered as a guess, for the next sync to check.
            coVerify(exactly = 1) { userPreferences.addPlaceholderConversation("conv-1") }
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

    // ── Checking a placeholder once the server has spoken ────────────────

    private fun placeholders(vararg ids: String) {
        every { userPreferences.placeholderConversations } returns flowOf(ids.toSet())
    }

    /** As a stand-in leaves it once a sync has filled in its members: still named for the stranger. */
    private fun chatWithAlice() {
        coEvery { conversationDao.getConversationById("conv-1") } returns
            ConversationEntity(
                id = "conv-1",
                type = "DIRECT",
                title = "Mallory",
                avatarUrl = "https://cdn/mallory.jpg",
                participantIds = """["self","alice"]""",
                unreadCount = 2,
                updatedAt = 1,
                createdAt = 1,
            )
        coEvery { resolver.displayNameFor("alice") } returns "Alice herself"
    }

    /**
     * A placeholder is filed under an id its first message chose. If that id
     * is this user's chat with someone else, the message does not belong.
     */
    @Test
    fun `a message that made a placeholder for someone else's chat is removed once the server says whose chat it is`() =
        runTest {
            placeholders("conv-1")
            chatWithAlice()
            coEvery { messageDao.sendersInConversation("conv-1") } returns
                listOf(MessageSender("from-mallory", "mallory"), MessageSender("from-alice", "alice"), MessageSender("mine", "self"))

            assertEquals(1, repo.settlePlaceholderConversations(setOf("conv-1")))

            coVerify(exactly = 1) { messageDao.deleteMessages(listOf("from-mallory")) }
            coVerify(exactly = 1) { mediaEvictor.evict("from-mallory") }
            // Nor is the chat left named after them, or counting their message.
            val renamed = slot<ConversationEntity>()
            coVerify(exactly = 1) { conversationDao.updateConversation(capture(renamed)) }
            assertEquals("Alice herself", renamed.captured.title)
            assertEquals(null, renamed.captured.avatarUrl)
            assertEquals(1, renamed.captured.unreadCount)
            coVerify(exactly = 1) { notificationHandler.cancelNotificationsForConversation("conv-1") }
            coVerify(exactly = 1) { userPreferences.removePlaceholderConversations(setOf("conv-1")) }
        }

    @Test
    fun `a placeholder that was right loses nothing and stops being a guess`() =
        runTest {
            placeholders("conv-1")
            chatWithAlice()
            coEvery { messageDao.sendersInConversation("conv-1") } returns
                listOf(MessageSender("from-alice", "alice"), MessageSender("mine", "self"))

            assertEquals(0, repo.settlePlaceholderConversations(setOf("conv-1")))

            coVerify(exactly = 0) { messageDao.deleteMessages(any()) }
            coVerify(exactly = 1) { userPreferences.removePlaceholderConversations(setOf("conv-1")) }
        }

    @Test
    fun `a placeholder the server has not listed is left for a later sync`() =
        runTest {
            placeholders("conv-1")

            assertEquals(0, repo.settlePlaceholderConversations(setOf("some-other-chat")))

            coVerify(exactly = 0) { messageDao.sendersInConversation(any()) }
            coVerify(exactly = 0) { userPreferences.removePlaceholderConversations(any()) }
        }

    /** Only chats this phone guessed at are ever checked this way. */
    @Test
    fun `a conversation that was never a placeholder is not touched`() =
        runTest {
            placeholders()

            assertEquals(0, repo.settlePlaceholderConversations(setOf("conv-1")))

            coVerify(exactly = 0) { messageDao.sendersInConversation(any()) }
            coVerify(exactly = 0) { messageDao.deleteMessages(any()) }
        }
}
