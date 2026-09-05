package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanchr.core.database.entity.SignalIdentityEntity

@Dao
interface SignalIdentityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(identity: SignalIdentityEntity)

    @Query("SELECT * FROM signal_identities WHERE address = :address")
    suspend fun get(address: String): SignalIdentityEntity?

    @Query("DELETE FROM signal_identities WHERE address = :address")
    suspend fun delete(address: String)

    @Query("SELECT * FROM signal_identities WHERE address = :address")
    fun getBlocking(address: String): SignalIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBlocking(entity: SignalIdentityEntity)

    @Query("DELETE FROM signal_identities")
    fun deleteAllBlocking()

    /**
     * Records (or with null, revokes) a manual safety-number verification
     * without touching the key itself.
     */
    @Query("UPDATE signal_identities SET verified_at = :verifiedAt WHERE address = :address")
    fun setVerifiedAtBlocking(
        address: String,
        verifiedAt: Long?,
    )
}
