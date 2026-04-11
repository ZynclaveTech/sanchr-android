package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanchr.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query(
        """
        SELECT * FROM messages
        WHERE conversation_id = :conversationId AND is_deleted = 0
        ORDER BY timestamp ASC
        """
    )
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE conversation_id = :conversationId AND is_deleted = 0
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getMessagesPaginated(
        conversationId: String,
        limit: Int,
        offset: Int,
    ): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("UPDATE messages SET is_deleted = 1 WHERE id = :messageId")
    suspend fun softDeleteMessage(messageId: String)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteAllMessagesInConversation(conversationId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query(
        """
        DELETE FROM messages
        WHERE expires_at IS NOT NULL AND expires_at < :currentTimeMillis
        """
    )
    suspend fun deleteExpiredMessages(currentTimeMillis: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE conversation_id = :conversationId AND status != 'READ'")
    fun observeUnreadCount(conversationId: String): Flow<Int>
}
