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
}
