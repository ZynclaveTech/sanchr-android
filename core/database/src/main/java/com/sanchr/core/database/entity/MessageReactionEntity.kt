package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One user's emoji on one message. No foreign key on purpose: a reaction
 * event can arrive on the stream before the message it targets has been
 * decrypted and persisted, and must survive until the row exists.
 */
@Entity(
    tableName = "message_reactions",
    primaryKeys = ["message_id", "user_id", "emoji"],
    indices = [Index(value = ["message_id"])],
)
data class MessageReactionEntity(
    @ColumnInfo(name = "message_id")
    val messageId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "emoji")
    val emoji: String,
    /** Epoch millis from the sender. */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
)
