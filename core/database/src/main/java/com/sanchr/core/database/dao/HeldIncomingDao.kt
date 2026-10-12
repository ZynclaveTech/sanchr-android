package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sanchr.core.database.entity.HeldIncomingEntity

@Dao
interface HeldIncomingDao {
    @Insert
    suspend fun hold(held: HeldIncomingEntity): Long

    /** Oldest first: messages are filed in the order they came. */
    @Query("SELECT * FROM held_incoming ORDER BY id ASC")
    suspend fun all(): List<HeldIncomingEntity>

    @Query("DELETE FROM held_incoming WHERE id = :id")
    suspend fun release(id: Long)

    @Query("UPDATE held_incoming SET attempts = attempts + 1 WHERE id = :id")
    suspend fun noteAttempt(id: Long)

    @Query("DELETE FROM held_incoming")
    suspend fun deleteAll()
}
