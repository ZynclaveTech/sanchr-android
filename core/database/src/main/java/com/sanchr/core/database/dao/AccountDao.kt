package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanchr.core.database.entity.AccountEntity

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE user_id = :userId")
    suspend fun get(userId: String): AccountEntity?

    @Query("SELECT * FROM accounts LIMIT 1")
    suspend fun getCurrent(): AccountEntity?

    @Query("DELETE FROM accounts WHERE user_id = :userId")
    suspend fun delete(userId: String)

    // ------------------------------------------------------------------
    // Blocking variants — called from SignalDispatcher seam by libsignal
    // stores, whose interfaces are synchronous. Safe because SignalDispatcher
    // is a single-threaded confined dispatcher.
    // ------------------------------------------------------------------

    @Query("SELECT * FROM accounts LIMIT 1")
    fun getCurrentBlocking(): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBlocking(account: AccountEntity)

    @Query("DELETE FROM accounts")
    fun deleteAllBlocking()
}
