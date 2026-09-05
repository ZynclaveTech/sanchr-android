package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanchr.core.database.entity.MessageReactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageReactionDao {
    /** Every reaction on every message of [conversationId]; joined so the flow re-emits with the message list. */
    @Query(
        """
        SELECT r.* FROM message_reactions r
        INNER JOIN messages m ON m.id = r.message_id
        WHERE m.conversation_id = :conversationId
        ORDER BY r.timestamp ASC
        """,
    )
    fun observeForConversation(conversationId: String): Flow<List<MessageReactionEntity>>

    @Query("SELECT * FROM message_reactions WHERE message_id = :messageId ORDER BY timestamp ASC")
    suspend fun forMessage(messageId: String): List<MessageReactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reaction: MessageReactionEntity)

    @Query("DELETE FROM message_reactions WHERE message_id = :messageId AND user_id = :userId AND emoji = :emoji")
    suspend fun delete(
        messageId: String,
        userId: String,
        emoji: String,
    )

    @Query("DELETE FROM message_reactions")
    suspend fun deleteAll()
}
