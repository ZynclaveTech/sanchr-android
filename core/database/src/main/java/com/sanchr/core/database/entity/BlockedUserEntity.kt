package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Someone this phone refuses messages from.
 *
 * A table of its own because a block has to hold for anyone, and it used to
 * be a column on `contacts` — which has a row only for people in the address
 * book. Blocking a stranger updated no row at all: the app said "blocked" and
 * went on delivering their messages. A sealed 1:1 message hides its sender
 * from the server, so this phone is the only place a block can be enforced.
 */
@Entity(tableName = "blocked_users")
data class BlockedUserEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,
    /** Epoch millis. */
    @ColumnInfo(name = "blocked_at")
    val blockedAt: Long,
)
