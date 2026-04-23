package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanchr.core.database.entity.SignalSessionEntity

@Dao
interface SignalSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SignalSessionEntity)

    @Query("SELECT * FROM signal_sessions WHERE address = :address")
    suspend fun get(address: String): SignalSessionEntity?

    @Query("DELETE FROM signal_sessions WHERE address = :address")
    suspend fun delete(address: String)
}
