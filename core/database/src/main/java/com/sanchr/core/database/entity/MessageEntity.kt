package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversation_id", "timestamp"]),
        Index(value = ["sender_id"]),
        Index(value = ["status"]),
        Index(value = ["status", "last_attempt_at"]),
    ],
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    @ColumnInfo(name = "sender_id")
    val senderId: String,
    @ColumnInfo(name = "content_type")
    val contentType: String, // "text", "image", "voice", "file", "location"
    @ColumnInfo(name = "content_body")
    val contentBody: String, // Encrypted content or JSON payload
    @ColumnInfo(name = "status")
    val status: String, // MessageStatus enum name
    @ColumnInfo(name = "timestamp")
    val timestamp: Long, // Epoch millis
    @ColumnInfo(name = "edited_at")
    val editedAt: Long? = null,
    @ColumnInfo(name = "reply_to_id")
    val replyToId: String? = null,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "attempts", defaultValue = "0")
    val attempts: Int = 0,
    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long? = null,
)
