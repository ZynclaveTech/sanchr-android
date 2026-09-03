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

    @Query("SELECT * FROM signal_sessions WHERE address = :address")
    fun getBlocking(address: String): SignalSessionEntity?

    @Query("SELECT address FROM signal_sessions WHERE address LIKE :prefix")
    fun listAddressesBlocking(prefix: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBlocking(entity: SignalSessionEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM signal_sessions WHERE address = :address)")
    fun existsBlocking(address: String): Boolean

    @Query("DELETE FROM signal_sessions WHERE address = :address")
    fun deleteBlocking(address: String)

    @Query("DELETE FROM signal_sessions WHERE address LIKE :prefix")
    fun deleteByPrefixBlocking(prefix: String)

    @Query("DELETE FROM signal_sessions")
    fun deleteAllBlocking()
}
