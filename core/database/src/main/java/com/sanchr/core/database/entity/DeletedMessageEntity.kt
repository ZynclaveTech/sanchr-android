package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The id of a message removed from this phone on purpose, kept so the message
 * cannot be saved again if it is delivered again. Nothing of the message.
 *
 * Delete Message leaves its row behind, emptied, and that row is what keeps
 * its id taken. Clearing a chat and deleting a conversation remove the rows
 * outright, which left every id in them free: the server delivering one of
 * those messages a second time — as it does when an acknowledgement is lost —
 * put it straight back.
 */
@Entity(tableName = "deleted_messages")
data class DeletedMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    /** Epoch millis. */
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long,
)
