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

    @Query("SELECT * FROM signal_signed_prekeys WHERE prekey_id = :prekeyId")
    fun getBlocking(prekeyId: Int): SignalSignedPreKeyEntity?

    @Query("SELECT * FROM signal_signed_prekeys")
    fun getAllBlocking(): List<SignalSignedPreKeyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBlocking(entity: SignalSignedPreKeyEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM signal_signed_prekeys WHERE prekey_id = :prekeyId)")
    fun existsBlocking(prekeyId: Int): Boolean

    @Query("DELETE FROM signal_signed_prekeys WHERE prekey_id = :prekeyId")
    fun deleteBlocking(prekeyId: Int)

    @Query("SELECT MAX(prekey_id) FROM signal_signed_prekeys")
    fun maxIdBlocking(): Int?

    @Query("DELETE FROM signal_signed_prekeys")
    fun deleteAllBlocking()
}
