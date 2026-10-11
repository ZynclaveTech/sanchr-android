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
