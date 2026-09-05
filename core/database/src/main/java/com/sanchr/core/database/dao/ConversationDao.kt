package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanchr.core.database.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query(
        """
        SELECT * FROM conversations
        WHERE is_archived = 0
        ORDER BY is_pinned DESC, updated_at DESC
        """,
    )
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT * FROM conversations
        WHERE is_archived = 1
        ORDER BY updated_at DESC
        """,
    )
    fun observeArchivedConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversationById(conversationId: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    fun observeConversation(conversationId: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    suspend fun getAllConversations(): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationEntity>)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    /**
     * Ids of DIRECT conversations whose participant list contains
     * [quotedUserId]. `participant_ids` is a JSON array of strings, so the
     * caller passes the id *with its JSON quotes* (`"\"<userId>\""`); the
     * quotes keep a user id from matching as a substring of a longer one.
     */
    @Query(
        """
        SELECT id FROM conversations
        WHERE type = 'DIRECT' AND participant_ids LIKE '%' || :quotedUserId || '%'
        """,
    )
    suspend fun directConversationIdsWith(quotedUserId: String): List<String>

    @Query("UPDATE conversations SET title = :title WHERE id = :conversationId")
    suspend fun updateTitle(
        conversationId: String,
        title: String?,
    )

    @Query("UPDATE conversations SET unread_count = 0 WHERE id = :conversationId")
    suspend fun markAsRead(conversationId: String)

    @Query("UPDATE conversations SET is_pinned = :isPinned WHERE id = :conversationId")
    suspend fun setPinned(
        conversationId: String,
        isPinned: Boolean,
    )

    @Query("UPDATE conversations SET is_muted = :isMuted WHERE id = :conversationId")
    suspend fun setMuted(
        conversationId: String,
        isMuted: Boolean,
    )

    @Query("UPDATE conversations SET is_archived = :isArchived WHERE id = :conversationId")
    suspend fun setArchived(
        conversationId: String,
        isArchived: Boolean,
    )

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()
}
