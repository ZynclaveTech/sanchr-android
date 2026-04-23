package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanchr.core.database.entity.QuarantinedEnvelopeEntity

@Dao
interface QuarantinedEnvelopeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: QuarantinedEnvelopeEntity)

    @Query("SELECT * FROM quarantined_envelopes WHERE envelope_id = :envelopeId")
    suspend fun get(envelopeId: String): QuarantinedEnvelopeEntity?

    @Query("DELETE FROM quarantined_envelopes WHERE envelope_id = :envelopeId")
    suspend fun delete(envelopeId: String)

    @Query("UPDATE quarantined_envelopes SET attempts = attempts + 1 WHERE envelope_id = :envelopeId")
    suspend fun incrementAttempts(envelopeId: String)

    // Blocking variants — used by worker + migration integrity assertions.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBlocking(entity: QuarantinedEnvelopeEntity)

    @Query("SELECT * FROM quarantined_envelopes WHERE envelope_id = :envelopeId")
    fun getBlocking(envelopeId: String): QuarantinedEnvelopeEntity?

    @Query("DELETE FROM quarantined_envelopes WHERE envelope_id = :envelopeId")
    fun deleteBlocking(envelopeId: String)

    @Query("SELECT COUNT(*) FROM quarantined_envelopes")
    fun countBlocking(): Int
}
