package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanchr.core.database.entity.PendingMessageAckEntity

@Dao
interface PendingMessageAckDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAck(ack: PendingMessageAckEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAcks(acks: List<PendingMessageAckEntity>)

    @Query(
        """
        SELECT * FROM pending_message_acks
        ORDER BY created_at ASC
        LIMIT :limit
        """,
    )
    suspend fun getPendingAcks(limit: Int): List<PendingMessageAckEntity>

    @Query(
        """
        DELETE FROM pending_message_acks
        WHERE conversation_id = :conversationId AND message_id = :messageId
        """,
    )
    suspend fun deleteAck(
        conversationId: String,
        messageId: String,
    )

    @Query("DELETE FROM pending_message_acks")
    suspend fun deleteAll()
}
