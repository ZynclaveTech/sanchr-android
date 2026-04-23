package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanchr.core.database.entity.SignalSignedPreKeyEntity

@Dao
interface SignalSignedPreKeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(signedPreKey: SignalSignedPreKeyEntity)

    @Query("SELECT * FROM signal_signed_prekeys WHERE prekey_id = :prekeyId")
    suspend fun get(prekeyId: Int): SignalSignedPreKeyEntity?

    @Query("DELETE FROM signal_signed_prekeys WHERE prekey_id < :prekeyId")
    suspend fun deleteOlderThan(prekeyId: Int)
}
