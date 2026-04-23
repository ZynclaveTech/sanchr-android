package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanchr.core.database.entity.EnvelopeQueueEntity

@Dao
interface EnvelopeQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(envelope: EnvelopeQueueEntity)

    @Query(
        """
        SELECT * FROM envelope_queue
        WHERE processed = 0
        ORDER BY received_at ASC
        LIMIT :limit
        """,
    )
    suspend fun listUnprocessed(limit: Int): List<EnvelopeQueueEntity>

    @Query("UPDATE envelope_queue SET processed = 1 WHERE envelope_id = :envelopeId")
    suspend fun markProcessed(envelopeId: String)

    @Query("UPDATE envelope_queue SET attempts = attempts + 1 WHERE envelope_id = :envelopeId")
    suspend fun incrementAttempts(envelopeId: String)

    @Query("DELETE FROM envelope_queue WHERE envelope_id = :envelopeId")
    suspend fun delete(envelopeId: String)
}
