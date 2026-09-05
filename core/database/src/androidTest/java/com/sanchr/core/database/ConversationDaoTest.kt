package com.sanchr.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationDaoTest {
    private lateinit var db: SanchrDatabase

    @Before
    fun open() {
        db =
            Room
                .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SanchrDatabase::class.java)
                .build()
    }

    @After
    fun close() = db.close()

    private fun row(
        id: String = "c1",
        title: String? = "server title",
        unread: Int = 0,
        archived: Boolean = false,
        timerMs: Long? = null,
        lastMessageId: String? = null,
    ) = ConversationEntity(
        id = id,
        type = "DIRECT",
        title = title,
        participantIds = """["self","peer"]""",
        unreadCount = unread,
        isArchived = archived,
        disappearingDurationMs = timerMs,
        lastMessageId = lastMessageId,
        updatedAt = 1L,
        createdAt = 1L,
    )

    @Test
    fun upsertFromServer_keeps_local_only_state_on_an_existing_row() =
        runBlocking {
            val dao = db.conversationDao()
            dao.insertConversation(row(archived = true, timerMs = 86_400_000L, lastMessageId = "m-9"))

            // What a server refresh carries: no archived flag, no timer, no
            // last message id — but a fresh unread count and title.
            dao.upsertFromServer(listOf(row(title = "~Alice", unread = 3)))

            val stored = requireNotNull(dao.getConversationById("c1"))
            assertTrue(stored.isArchived)
            assertEquals(86_400_000L, stored.disappearingDurationMs)
            assertEquals("m-9", stored.lastMessageId)
            assertEquals("~Alice", stored.title)
            assertEquals(3, stored.unreadCount)
        }

    @Test
    fun upsertFromServer_inserts_a_new_row_as_given() =
        runBlocking {
            val dao = db.conversationDao()

            dao.upsertFromServer(listOf(row(id = "new", unread = 1)))

            val stored = requireNotNull(dao.getConversationById("new"))
            assertEquals(1, stored.unreadCount)
            assertNull(stored.disappearingDurationMs)
        }

    @Test
    fun plain_replace_insert_is_what_loses_the_state() =
        runBlocking {
            // Documents the behaviour upsertFromServer exists to avoid.
            val dao = db.conversationDao()
            dao.insertConversation(row(archived = true, timerMs = 5_000L))

            dao.insertConversations(listOf(row()))

            val stored = requireNotNull(dao.getConversationById("c1"))
            assertEquals(false, stored.isArchived)
            assertNull(stored.disappearingDurationMs)
        }

    @Test
    fun observeLatestPerConversation_returns_only_each_chats_newest_undeleted_message() =
        runBlocking {
            db.conversationDao().insertConversation(row(id = "c1"))
            db.conversationDao().insertConversation(row(id = "c2"))
            val dao = db.messageDao()

            fun msg(
                id: String,
                conversation: String,
                ts: Long,
                deleted: Boolean = false,
            ) = MessageEntity(
                id = id,
                conversationId = conversation,
                senderId = "peer",
                contentType = "text",
                contentBody = id,
                status = "DELIVERED",
                timestamp = ts,
                isDeleted = deleted,
            )
            dao.insertMessages(listOf(msg("a1", "c1", 1), msg("a2", "c1", 2), msg("a3", "c1", 3, deleted = true), msg("b1", "c2", 9)))

            val latest =
                dao
                    .observeLatestPerConversation()
                    .first()
                    .map { it.id }
                    .sorted()

            assertEquals(listOf("a2", "b1"), latest)
        }
}
