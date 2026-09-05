package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanchr.core.database.entity.ContactProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactProfileDao {
    @Query("SELECT * FROM contact_profiles WHERE user_id = :userId")
    suspend fun getByUserId(userId: String): ContactProfileEntity?

    @Query("SELECT * FROM contact_profiles")
    fun observeAll(): Flow<List<ContactProfileEntity>>

    @Query("SELECT user_id FROM contact_profiles")
    suspend fun allUserIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ContactProfileEntity)

    @Query("DELETE FROM contact_profiles WHERE user_id = :userId")
    suspend fun delete(userId: String)

    @Query("DELETE FROM contact_profiles")
    suspend fun deleteAll()
}
