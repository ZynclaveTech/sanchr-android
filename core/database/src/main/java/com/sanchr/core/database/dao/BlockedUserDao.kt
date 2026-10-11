package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanchr.core.database.entity.BlockedUserEntity

@Dao
interface BlockedUserDao {
    /** Blocking someone already blocked keeps the first time it was done. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun block(user: BlockedUserEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun blockAll(users: List<BlockedUserEntity>)

    @Query("DELETE FROM blocked_users WHERE user_id = :userId")
    suspend fun unblock(userId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_users WHERE user_id = :userId)")
    suspend fun isBlocked(userId: String): Boolean

    /** Most recently blocked first. */
    @Query("SELECT user_id FROM blocked_users ORDER BY blocked_at DESC, user_id ASC")
    suspend fun blockedUserIds(): List<String>

    @Query("DELETE FROM blocked_users")
    suspend fun deleteAll()
}
