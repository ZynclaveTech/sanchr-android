package com.sanchr.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.database.entity.MessageEntity
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Saving a conversation that is already here must not cost it its messages.
 *
 * `messages` is tied to `conversations` with `ON DELETE CASCADE`, and the
 * saves were `REPLACE`, which deletes the old row first. So every sync of a
 * conversation, and every chat opened from Contacts, emptied it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ConversationUpsertTest {
    private lateinit var db: SanchrDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SanchrDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun conversation(
        title: String? = null,
        archived: Boolean = false,
    ) = ConversationEntity(
        id = "c1",
        type = "DIRECT",
        title = title,
        participantIds = "[]",
        isArchived = archived,
        createdAt = 1,
        updatedAt = 1,
    )

    private suspend fun aConversationWithAMessage(archived: Boolean = false) {
        db.conversationDao().insertConversation(conversation(archived = archived))
        db.messageDao().insertMessage(
            MessageEntity(
                id = "m1",
                conversationId = "c1",
                senderId = "peer",
                contentType = "text",
                contentBody = "hello",
                status = "DELIVERED",
                timestamp = 1,
            ),
        )
    }

    private suspend fun messages() = db.messageDao().messageIdsInConversation("c1")

    private suspend fun stored() = db.conversationDao().getConversationById("c1")

    @Test
    fun `a sync of a conversation keeps its messages`() =
        runTest {
            aConversationWithAMessage(archived = true)

            db.conversationDao().upsertFromServer(listOf(conversation(title = "Alice")))

            assertEquals(listOf("m1"), messages())
            assertEquals("Alice", stored()?.title)
            assertEquals(true, stored()?.isArchived, "what only this phone knows survives a sync")
        }

    /**
     * What the server's list actually carries for a chat: its type and who
     * is in it. Pin, mute and times come back as "no", "no" and nothing; its
     * unread count only ever goes up.
     */
    private fun fromServer(
        participants: String = """["self","alice"]""",
        title: String? = null,
        unread: Int = 7,
    ) = ConversationEntity(
        id = "c1",
        type = "DIRECT",
        title = title,
        participantIds = participants,
        unreadCount = unread,
        isPinned = false,
        isMuted = false,
        lastMessagePreview = null,
        lastMessageTimestamp = null,
        createdAt = 999,
        updatedAt = 999,
    )

    private suspend fun aChatTheUserHasSetUp() =
        db.conversationDao().insertConversation(
            ConversationEntity(
                id = "c1",
                type = "DIRECT",
                title = "Alice",
                avatarUrl = "https://cdn/alice.jpg",
                participantIds = """["self"]""",
                unreadCount = 0,
                isPinned = true,
                isMuted = true,
                lastMessagePreview = "see you then",
                lastMessageTimestamp = 50,
                createdAt = 10,
                updatedAt = 50,
            ),
        )

    @Test
    fun `a sync does not unpin or unmute a chat`() =
        runTest {
            aChatTheUserHasSetUp()

            db.conversationDao().upsertFromServer(listOf(fromServer()))

            assertEquals(true, stored()?.isPinned)
            assertEquals(true, stored()?.isMuted)
        }

    /** The server is never told a chat was read; its count only goes up. */
    @Test
    fun `a sync does not bring back the unread badge on a chat that was read`() =
        runTest {
            aChatTheUserHasSetUp()

            db.conversationDao().upsertFromServer(listOf(fromServer(unread = 7)))

            assertEquals(0, stored()?.unreadCount)
        }

    @Test
    fun `a sync does not reset a chat's times or its preview`() =
        runTest {
            aChatTheUserHasSetUp()

            db.conversationDao().upsertFromServer(listOf(fromServer()))

            assertEquals(10L, stored()?.createdAt)
            assertEquals(50L, stored()?.updatedAt)
            assertEquals("see you then", stored()?.lastMessagePreview)
            assertEquals(50L, stored()?.lastMessageTimestamp)
        }

    @Test
    fun `a sync does bring who is in the chat, and a name when it has one`() =
        runTest {
            aChatTheUserHasSetUp()

            db.conversationDao().upsertFromServer(listOf(fromServer(title = "~Alice")))
            assertEquals("""["self","alice"]""", stored()?.participantIds)
            assertEquals("~Alice", stored()?.title)

            // No name this time: the one already here is not wiped.
            db.conversationDao().upsertFromServer(listOf(fromServer(title = null)))
            assertEquals("~Alice", stored()?.title)
            assertEquals("https://cdn/alice.jpg", stored()?.avatarUrl)
        }

    @Test
    fun `a chat seen for the first time is written as the server gave it`() =
        runTest {
            db.conversationDao().upsertFromServer(listOf(fromServer(unread = 2)))

            assertEquals(2, stored()?.unreadCount)
            assertEquals("""["self","alice"]""", stored()?.participantIds)
        }

    @Test
    fun `saving a conversation again keeps its messages`() =
        runTest {
            aConversationWithAMessage()

            db.conversationDao().insertConversation(conversation(title = "Alice"))

            assertEquals(listOf("m1"), messages())
            assertEquals("Alice", stored()?.title)
        }

    @Test
    fun `saving several conversations again keeps their messages`() =
        runTest {
            aConversationWithAMessage()

            db.conversationDao().insertConversations(listOf(conversation(title = "Alice")))

            assertEquals(listOf("m1"), messages())
            assertEquals("Alice", stored()?.title)
        }

    @Test
    fun `a conversation that was not here is added`() =
        runTest {
            db.conversationDao().upsertFromServer(listOf(conversation(title = "Alice")))

            assertEquals("Alice", stored()?.title)
        }

    /** Deleting the conversation is still what removes its messages. */
    @Test
    fun `deleting a conversation still takes its messages`() =
        runTest {
            aConversationWithAMessage()

            db.conversationDao().deleteConversation("c1")

            assertEquals(emptyList(), messages())
        }
}
