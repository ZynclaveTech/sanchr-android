package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["phone_number"], unique = true),
        Index(value = ["display_name"]),
        Index(value = ["is_registered"]),
    ],
)
data class ContactEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String? = null, // Null if not yet a Sanchr user
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "avatar_url")
    val avatarUrl: String? = null,
    @ColumnInfo(name = "is_registered")
    val isRegistered: Boolean = false, // Whether they're on Sanchr
    @ColumnInfo(name = "is_blocked")
    val isBlocked: Boolean = false,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long? = null,
)
