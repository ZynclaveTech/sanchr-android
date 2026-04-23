package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanchr.core.database.entity.SignalPreKeyEntity

@Dao
interface SignalPreKeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preKey: SignalPreKeyEntity)

    @Query("SELECT * FROM signal_prekeys WHERE prekey_id = :prekeyId")
    suspend fun get(prekeyId: Int): SignalPreKeyEntity?

    @Query("DELETE FROM signal_prekeys WHERE prekey_id = :prekeyId")
    suspend fun delete(prekeyId: Int)

    @Query("SELECT COUNT(*) FROM signal_prekeys")
    suspend fun count(): Int
}
