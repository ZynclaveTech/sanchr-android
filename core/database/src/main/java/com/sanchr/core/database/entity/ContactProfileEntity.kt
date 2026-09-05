package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A peer's profile as decrypted under *their* Profile Key, keyed by user id.
 *
 * Separate from [ContactEntity] on purpose: a profile arrives for anyone who
 * messages us, address-book contact or not, and `contacts.phone_number` is
 * unique, so an unknown sender cannot be given a contact row. Nothing here is
 * ever server plaintext; a row exists only once a key we received decrypted
 * the server's ciphertext. Display precedence over these fields lives in
 * `ContactDisplayName` (core:model).
 */
@Entity(tableName = "contact_profiles")
data class ContactProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "display_name")
    val displayName: String? = null,
    @ColumnInfo(name = "bio")
    val bio: String? = null,
    @ColumnInfo(name = "avatar_url")
    val avatarUrl: String? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
