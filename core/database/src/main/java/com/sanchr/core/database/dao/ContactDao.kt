package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanchr.core.database.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query(
        """
        SELECT * FROM contacts
        WHERE COALESCE(user_id, id) NOT IN (SELECT user_id FROM blocked_users)
        ORDER BY display_name ASC
        """,
    )
    fun observeContacts(): Flow<List<ContactEntity>>

    @Query(
        """
        SELECT * FROM contacts
        WHERE is_registered = 1 AND COALESCE(user_id, id) NOT IN (SELECT user_id FROM blocked_users)
        ORDER BY display_name ASC
        """,
    )
    fun observeRegisteredContacts(): Flow<List<ContactEntity>>

    @Query(
        """
        SELECT * FROM contacts
        WHERE is_favorite = 1 AND COALESCE(user_id, id) NOT IN (SELECT user_id FROM blocked_users)
        ORDER BY display_name ASC
        """,
    )
    fun observeFavoriteContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :contactId")
    suspend fun getContactById(contactId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE phone_number = :phoneNumber")
    suspend fun getContactByPhoneNumber(phoneNumber: String): ContactEntity?

    @Query("SELECT * FROM contacts ORDER BY display_name ASC")
    suspend fun getAllContacts(): List<ContactEntity>

    @Query(
        """
        SELECT * FROM contacts
        WHERE display_name LIKE '%' || :query || '%'
           OR phone_number LIKE '%' || :query || '%'
        ORDER BY display_name ASC
        """,
    )
    fun searchContacts(query: String): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Query("UPDATE contacts SET is_favorite = :isFavorite WHERE id = :contactId")
    suspend fun setFavorite(
        contactId: String,
        isFavorite: Boolean,
    )

    @Query("DELETE FROM contacts WHERE id = :contactId")
    suspend fun deleteContact(contactId: String)

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()
}
