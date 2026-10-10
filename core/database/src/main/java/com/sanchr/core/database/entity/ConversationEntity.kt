package com.sanchr.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["updated_at"]),
        Index(value = ["is_pinned"]),
    ],
)
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "type")
    val type: String, // "DIRECT" or "GROUP"
    @ColumnInfo(name = "title")
    val title: String? = null,
    @ColumnInfo(name = "avatar_url")
    val avatarUrl: String? = null,
    @ColumnInfo(name = "participant_ids")
    val participantIds: String, // JSON array of user IDs
    @ColumnInfo(name = "last_message_id")
    val lastMessageId: String? = null,
    @ColumnInfo(name = "last_message_preview")
    val lastMessagePreview: String? = null,
    @ColumnInfo(name = "last_message_timestamp")
    val lastMessageTimestamp: Long? = null,
    @ColumnInfo(name = "unread_count")
    val unreadCount: Int = 0,
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,
    @ColumnInfo(name = "is_muted")
    val isMuted: Boolean = false,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    /**
     * Hidden from every chat list on this device until the user restores it.
     *
     * Device-only, like [isArchived]: nothing on the wire carries it, and it
     * must survive a sync (see `ConversationDao.upsertFromServer`).
     */
    @ColumnInfo(name = "is_hidden")
    val isHidden: Boolean = false,
    /**
     * This chat's wallpaper, or null to follow the account-wide choice.
     *
     * Device-only, like [isArchived] and [isHidden]: nothing on the wire
     * carries it, so it must survive a sync.
     */
    @ColumnInfo(name = "wallpaper")
    val wallpaper: String? = null,
    /**
     * Unsent composer text for this chat, or null.
     *
     * In the database rather than DataStore because a draft *is* message
     * content, and message content belongs behind SQLCipher like the rest of
     * it. Device-only, like [isArchived] and [wallpaper]: a draft is by
     * definition something the user has not sent, so nothing on the wire
     * carries it and it must survive a sync. Deleting the conversation takes
     * the draft with it.
     */
    @ColumnInfo(name = "draft_text")
    val draftText: String? = null,
    /**
     * The per-chat vault policy (iOS `chatVaultPolicy`), three flags that all
     * start off. Device-only, like [wallpaper]: nothing on the wire carries
     * them, so they must survive a sync — a sync that reset them would
     * silently stop protecting media the user had asked to have protected.
     */
    @ColumnInfo(name = "vault_auto_incoming")
    val vaultAutoIncoming: Boolean = false,
    @ColumnInfo(name = "vault_view_once")
    val vaultViewOnce: Boolean = false,
    @ColumnInfo(name = "vault_capture_protection")
    val vaultCaptureProtection: Boolean = false,
    @ColumnInfo(name = "disappearing_duration_ms")
    val disappearingDurationMs: Long? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
