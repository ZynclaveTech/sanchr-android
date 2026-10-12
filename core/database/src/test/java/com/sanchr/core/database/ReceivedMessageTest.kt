package com.sanchr.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.database.entity.MessageEntity
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A message that arrives cannot replace one already held.
 *
 * Its id is chosen by whoever sent it, and they know the id of every message
 * in the chat they share with this user — theirs and this user's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ReceivedMessageTest {
    private lateinit var db: SanchrDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SanchrDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        runTest {
            db.conversationDao().insertConversation(
                ConversationEntity(id = "c1", type = "DIRECT", participantIds = "[]", createdAt = 1, updatedAt = 1),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun message(
        sender: String,
        body: String,
        status: String = "DELIVERED",
    ) = MessageEntity(
        id = "m1",
        conversationId = "c1",
        senderId = sender,
        contentType = "text",
        contentBody = body,
        status = status,
        timestamp = 1,
    )

    private suspend fun stored() = checkNotNull(db.messageDao().getMessageById("m1"))

    @Test
    fun `a new message is saved`() =
        runTest {
            assertNotEquals(-1L, db.messageDao().insertMessageIfAbsent(message("peer", "hello")))

            assertEquals("hello", stored().contentBody)
        }

    @Test
    fun `a message this user sent cannot be rewritten by the person it went to`() =
        runTest {
            db.messageDao().insertMessage(message("self", "I will pay you back Friday", status = "SENT"))

            assertEquals(-1L, db.messageDao().insertMessageIfAbsent(message("peer", "I owe you nothing")))

            assertEquals("I will pay you back Friday", stored().contentBody)
            assertEquals("self", stored().senderId)
            assertEquals("SENT", stored().status)
        }

    @Test
    fun `a sender cannot quietly change what they said earlier`() =
        runTest {
            db.messageDao().insertMessageIfAbsent(message("peer", "what I really said"))

            db.messageDao().insertMessageIfAbsent(message("peer", "what I wish I had said"))

            assertEquals("what I really said", stored().contentBody)
        }

    @Test
    fun `a deleted message stays deleted when it is sent again`() =
        runTest {
            db.messageDao().insertMessageIfAbsent(message("peer", "hello"))
            db.messageDao().softDeleteMessage("m1")

            db.messageDao().insertMessageIfAbsent(message("peer", "hello"))

            assertTrue(stored().isDeleted)
        }

    @Test
    fun `an opened view-once is not refilled when it is sent again`() =
        runTest {
            db.messageDao().insertMessageIfAbsent(message("peer", "{the photo}"))
            db.messageDao().replaceContent("m1", "text", "Opened")

            db.messageDao().insertMessageIfAbsent(message("peer", "{the photo}"))

            assertEquals("Opened", stored().contentBody)
        }

    @Test
    fun `a message already read is not handed back as unread`() =
        runTest {
            db.messageDao().insertMessageIfAbsent(message("peer", "hello", status = "READ"))

            db.messageDao().insertMessageIfAbsent(message("peer", "hello"))

            assertEquals("READ", stored().status)
        }

    // ── Cleared and deleted chats ────────────────────────────────────────

    /**
     * Clearing a chat removes its rows outright, which left their ids free:
     * the same message delivered again was saved as if it were new.
     */
    @Test
    fun `a message of a cleared chat does not come back when it is delivered again`() =
        runTest {
            db.messageDao().insertReceivedMessage(message("peer", "hello"))
            db.messageDao().deleteAllMessagesInConversation("c1")

            assertFalse(db.messageDao().insertReceivedMessage(message("peer", "hello")))

            assertEquals(null, db.messageDao().getMessageById("m1"))
            assertTrue(db.messageDao().wasDeleted("m1"))
        }

    @Test
    fun `nor does a message of a deleted chat, even once the chat exists again`() =
        runTest {
            db.messageDao().insertReceivedMessage(message("peer", "hello"))
            db.conversationDao().deleteConversation("c1")
            db.conversationDao().insertConversation(
                ConversationEntity(id = "c1", type = "DIRECT", participantIds = "[]", createdAt = 1, updatedAt = 1),
            )

            assertFalse(db.messageDao().insertReceivedMessage(message("peer", "hello")))
        }

    @Test
    fun `a new message in a cleared chat is saved`() =
        runTest {
            db.messageDao().insertReceivedMessage(message("peer", "hello"))
            db.messageDao().deleteAllMessagesInConversation("c1")

            assertTrue(db.messageDao().insertReceivedMessage(message("peer", "hello again").copy(id = "m2")))
        }

    @Test
    fun `a message never seen was not deleted`() =
        runTest {
            assertFalse(db.messageDao().wasDeleted("never"))
        }

    @Test
    fun `a deleted id is forgotten once nothing could send it again`() =
        runTest {
            db.messageDao().insertReceivedMessage(message("peer", "hello"))
            db.messageDao().deleteAllMessagesInConversation("c1")
            val helper = db.openHelper.writableDatabase
            val now = System.currentTimeMillis()

            ReactionCleanup.forgetOldDeletedIds(helper, now + ReactionCleanup.DELETED_ID_RETENTION_MILLIS - 60_000)
            assertTrue(db.messageDao().wasDeleted("m1"))

            ReactionCleanup.forgetOldDeletedIds(helper, now + ReactionCleanup.DELETED_ID_RETENTION_MILLIS + 60_000)
            assertFalse(db.messageDao().wasDeleted("m1"))
        }

    // ── Deleting ─────────────────────────────────────────────────────────

    /** The row stays, to keep the id taken. What it said does not. */
    @Test
    fun `a deleted message keeps its place and none of its content`() =
        runTest {
            db.messageDao().insertMessageIfAbsent(message("peer", "something private"))

            db.messageDao().softDeleteMessage("m1")

            assertTrue(stored().isDeleted)
            assertEquals("", stored().contentBody)
            assertEquals("text", stored().contentType)
            assertEquals("peer", stored().senderId)
        }

    @Test
    fun `messages deleted before deleting emptied them are found and emptied`() =
        runTest {
            db.messageDao().insertMessage(message("peer", "left behind").copy(isDeleted = true))
            assertEquals(listOf("m1"), db.messageDao().deletedMessageIdsStillHoldingContent())

            assertEquals(1, db.messageDao().emptyDeletedMessages())

            assertEquals("", stored().contentBody)
            assertEquals(emptyList(), db.messageDao().deletedMessageIdsStillHoldingContent())
            assertEquals(0, db.messageDao().emptyDeletedMessages())
        }

    // ── Receipts ─────────────────────────────────────────────────────────

    private val notYetRead = listOf("QUEUED", "SENDING", "SENT", "FAILED", "DELIVERED")

    @Test
    fun `a receipt moves this user's own message forward`() =
        runTest {
            db.messageDao().insertMessage(message("self", "hello", status = "SENT"))

            assertEquals(1, db.messageDao().advanceOwnMessageStatus("m1", "self", "READ", notYetRead))

            assertEquals("READ", stored().status)
        }

    @Test
    fun `a receipt cannot touch a message someone else sent`() =
        runTest {
            db.messageDao().insertMessage(message("peer", "hello", status = "DELIVERED"))

            assertEquals(0, db.messageDao().advanceOwnMessageStatus("m1", "self", "READ", notYetRead))

            assertEquals("DELIVERED", stored().status)
        }

    @Test
    fun `a receipt cannot pull a read message back`() =
        runTest {
            db.messageDao().insertMessage(message("self", "hello", status = "READ"))

            assertEquals(
                0,
                db.messageDao().advanceOwnMessageStatus("m1", "self", "DELIVERED", listOf("QUEUED", "SENDING", "SENT", "FAILED")),
            )

            assertEquals("READ", stored().status)
        }

    @Test
    fun `a receipt for a message that is not here changes nothing`() =
        runTest {
            assertEquals(0, db.messageDao().advanceOwnMessageStatus("nope", "self", "READ", notYetRead))
        }
}
