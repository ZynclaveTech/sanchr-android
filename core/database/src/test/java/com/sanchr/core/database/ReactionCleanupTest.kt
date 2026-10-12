package com.sanchr.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.database.entity.MessageReactionEntity
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A message's reactions go when the message does, however it goes. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ReactionCleanupTest {
    private lateinit var db: SanchrDatabase
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SanchrDatabase::class.java)
                .allowMainThreadQueries()
                .addCallback(ReactionCleanup)
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun conversation(id: String) =
        db.conversationDao().insertConversation(
            ConversationEntity(id = id, type = "DIRECT", participantIds = "[]", createdAt = now, updatedAt = now),
        )

    private fun entity(
        id: String,
        conversationId: String,
        expiresAt: Long? = null,
    ) = MessageEntity(
        id = id,
        conversationId = conversationId,
        senderId = "peer",
        contentType = "text",
        contentBody = "hello",
        status = "DELIVERED",
        timestamp = now,
        expiresAt = expiresAt,
    )

    private suspend fun message(
        id: String,
        conversationId: String = "c1",
        expiresAt: Long? = null,
    ) = db.messageDao().insertMessage(entity(id, conversationId, expiresAt))

    private suspend fun react(
        messageId: String,
        timestamp: Long = now,
    ) = db.messageReactionDao().upsert(MessageReactionEntity(messageId, "peer", "👍", timestamp))

    private suspend fun reactionsOn(messageId: String) = db.messageReactionDao().forMessage(messageId).size

    @Test
    fun `clearing a chat takes its reactions and leaves another chat's alone`() =
        runTest {
            conversation("c1")
            conversation("c2")
            message("m1")
            message("m2", conversationId = "c2")
            react("m1")
            react("m2")

            db.messageDao().deleteAllMessagesInConversation("c1")

            assertEquals(0, reactionsOn("m1"))
            assertEquals(1, reactionsOn("m2"))
        }

    @Test
    fun `deleting one message takes its reactions`() =
        runTest {
            conversation("c1")
            message("m1")
            message("m2")
            react("m1")
            react("m2")

            db.messageDao().softDeleteMessage("m1")

            assertEquals(0, reactionsOn("m1"))
            assertEquals(1, reactionsOn("m2"))
        }

    @Test
    fun `a disappearing message that expires takes its reactions`() =
        runTest {
            conversation("c1")
            message("gone", expiresAt = now - 1)
            message("kept", expiresAt = now + 60_000)
            react("gone")
            react("kept")

            db.messageDao().deleteExpiredMessages(now)

            assertEquals(0, reactionsOn("gone"))
            assertEquals(1, reactionsOn("kept"))
        }

    @Test
    fun `deleting a conversation takes the reactions on its messages`() =
        runTest {
            conversation("c1")
            message("m1")
            react("m1")

            db.conversationDao().deleteConversation("c1")

            assertEquals(0, reactionsOn("m1"))
        }

    /** Saving a message again replaces its row. That is not the message going. */
    @Test
    fun `saving a message again keeps its reactions`() =
        runTest {
            conversation("c1")
            message("m1")
            react("m1")

            db.messageDao().insertMessage(entity("m1", "c1").copy(status = "READ"))

            assertEquals(1, reactionsOn("m1"))
        }

    /** The reason there is no foreign key. */
    @Test
    fun `a reaction that arrives before its message waits for it`() =
        runTest {
            conversation("c1")
            react("early")

            ReactionCleanup.sweep(db.openHelper.writableDatabase, now)
            message("early")

            assertEquals(1, reactionsOn("early"))
        }

    @Test
    fun `the sweep removes what was left behind before the triggers existed`() =
        runTest {
            conversation("c1")
            message("here")
            react("here", timestamp = now - 2 * ReactionCleanup.ORPHAN_GRACE_MILLIS)
            react("long-gone", timestamp = now - ReactionCleanup.ORPHAN_GRACE_MILLIS - 1)
            react("maybe-late", timestamp = now - ReactionCleanup.ORPHAN_GRACE_MILLIS + 1)

            ReactionCleanup.sweep(db.openHelper.writableDatabase, now)

            assertEquals(1, reactionsOn("here"))
            assertEquals(0, reactionsOn("long-gone"))
            assertEquals(1, reactionsOn("maybe-late"))
        }

    @Test
    fun `the sweep takes reactions still sitting on a deleted message`() =
        runTest {
            conversation("c1")
            message("m1")
            db.messageDao().flagDeleted("m1")
            react("m1")

            ReactionCleanup.sweep(db.openHelper.writableDatabase, now)

            assertEquals(0, reactionsOn("m1"))
        }

    // ── Expiry ───────────────────────────────────────────────────────────

    @Test
    fun `a message is due at its deadline, not a millisecond after`() =
        runTest {
            conversation("c1")
            message("due", expiresAt = now)
            message("not-yet", expiresAt = now + 1)
            message("forever")

            assertEquals(listOf("due"), db.messageDao().expiredMessageIds(now))
            assertEquals(1, db.messageDao().deleteExpiredMessages(now))
            assertEquals(listOf("forever", "not-yet"), db.messageDao().messageIdsInConversation("c1").sorted())
        }

    @Test
    fun `the next deadline is the earliest one held, and none when nothing disappears`() =
        runTest {
            conversation("c1")
            message("forever")
            assertEquals(null, db.messageDao().observeNextExpiry().first())

            message("later", expiresAt = now + 60_000)
            message("sooner", expiresAt = now + 5_000)

            assertEquals(now + 5_000, db.messageDao().observeNextExpiry().first())
        }
}
