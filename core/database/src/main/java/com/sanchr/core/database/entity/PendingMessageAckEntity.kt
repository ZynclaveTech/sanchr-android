package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "pending_message_acks",
    primaryKeys = ["conversation_id", "message_id"],
    indices = [Index(value = ["created_at"])],
)
data class PendingMessageAckEntity(
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    @ColumnInfo(name = "message_id")
    val messageId: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
