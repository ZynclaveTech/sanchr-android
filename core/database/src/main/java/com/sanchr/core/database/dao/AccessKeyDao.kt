package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanchr.core.database.entity.AccessKeyEntity

@Dao
interface AccessKeyDao {
    @Query("SELECT * FROM access_keys WHERE media_id = :mediaId")
    suspend fun get(mediaId: String): AccessKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AccessKeyEntity)

    @Query("UPDATE access_keys SET last_accessed_at = :nowMillis WHERE media_id = :mediaId")
    suspend fun touch(
        mediaId: String,
        nowMillis: Long,
    )

    /** Deletes entries whose sliding window (newer of created/last accessed) ended before [cutoffMillis]. */
    @Query("DELETE FROM access_keys WHERE MAX(created_at, last_accessed_at) < :cutoffMillis")
    suspend fun purgeExpired(cutoffMillis: Long): Int

    @Query("DELETE FROM access_keys WHERE media_id = :mediaId")
    suspend fun delete(mediaId: String)

    @Query("DELETE FROM access_keys")
    suspend fun deleteAll()
}
