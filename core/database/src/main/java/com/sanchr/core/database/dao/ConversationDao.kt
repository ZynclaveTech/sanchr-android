package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.sanchr.core.database.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query(
        """
        SELECT * FROM conversations
        WHERE is_archived = 0 AND is_hidden = 0
        ORDER BY is_pinned DESC, updated_at DESC
        """,
    )
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT * FROM conversations
        WHERE is_archived = 1 AND is_hidden = 0
        ORDER BY updated_at DESC
        """,
    )
    fun observeArchivedConversations(): Flow<List<ConversationEntity>>

    /**
     * Hidden chats, which is the only list that shows them. Archived state is
     * ignored here: a chat the user hid should appear in exactly one place,
     * whatever it was before.
     */
    @Query(
        """
        SELECT * FROM conversations
        WHERE is_hidden = 1
        ORDER BY updated_at DESC
        """,
    )
    fun observeHiddenConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversationById(conversationId: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    fun observeConversation(conversationId: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    suspend fun getAllConversations(): List<ConversationEntity>

    /**
     * Saves a conversation, overwriting every column of one already here.
     *
     * An upsert, never `REPLACE`. `REPLACE` deletes the old row to make way
     * for the new one, and `messages` hangs off this table with
     * `ON DELETE CASCADE` — so saving a conversation that already existed
     * deleted every message in it. Each periodic sync, and each chat opened
     * from Contacts, did exactly that.
     */
    @Upsert
    suspend fun insertConversation(conversation: ConversationEntity)

    /** As [insertConversation], for several. */
    @Upsert
    suspend fun insertConversations(conversations: List<ConversationEntity>)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    /**
     * Writes what the server knows about these conversations without
     * discarding what only this device knows.
     *
     * What the server knows is little: which conversations exist, of what
     * type, and who is in them. Everything else on the row is this phone's,
     * and a refresh that overwrote it undid the user's own choices on every
     * periodic sync:
     *
     * - `is_pinned`, `is_muted`: the server's list carries neither, so every
     *   pin was dropped and every muted chat unmuted.
     * - `unread_count`: the server only ever counts up — it is never told a
     *   chat was read — so a chat that had been read showed as unread again.
     * - `updated_at`, `created_at`, `last_message_preview`,
     *   `last_message_timestamp`: absent from the server's list, so they
     *   were reset to "now" or to nothing.
     * - `is_archived`, `is_hidden`, `wallpaper`, `draft_text`, the three
     *   `vault_*` flags, `disappearing_duration_ms`, `last_message_id`: not
     *   on the wire at all.
     *
     * A conversation not yet here is written as given.
     */
    @Transaction
    suspend fun upsertFromServer(conversations: List<ConversationEntity>) {
        for (incoming in conversations) {
            val local = getConversationById(incoming.id)
            insertConversation(
                if (local == null) {
                    incoming
                } else {
                    local.copy(
                        type = incoming.type,
                        participantIds = incoming.participantIds,
                        // Resolved by the caller from who is in it now.
                        title = incoming.title ?: local.title,
                        avatarUrl = incoming.avatarUrl ?: local.avatarUrl,
                    )
                },
            )
        }
    }

    /**
     * Ids of DIRECT conversations whose participant list contains
     * [quotedUserId]. `participant_ids` is a JSON array of strings, so the
     * caller passes the id *with its JSON quotes* (`"\"<userId>\""`); the
     * quotes keep a user id from matching as a substring of a longer one.
     */
    @Query(
        """
        SELECT id FROM conversations
        WHERE type = 'DIRECT' AND participant_ids LIKE '%' || :quotedUserId || '%'
        """,
    )
    suspend fun directConversationIdsWith(quotedUserId: String): List<String>

    @Query("UPDATE conversations SET title = :title WHERE id = :conversationId")
    suspend fun updateTitle(
        conversationId: String,
        title: String?,
    )

    @Query("UPDATE conversations SET unread_count = 0 WHERE id = :conversationId")
    suspend fun markAsRead(conversationId: String)

    /** A new inbound message landed: one more unread, and the chat moves up (as iOS bumps `unreadCount` on save). */
    @Query(
        """
        UPDATE conversations
        SET unread_count = unread_count + 1,
            updated_at = MAX(updated_at, :timestamp)
        WHERE id = :conversationId
        """,
    )
    suspend fun incrementUnread(
        conversationId: String,
        timestamp: Long,
    )

    @Query("UPDATE conversations SET is_pinned = :isPinned WHERE id = :conversationId")
    suspend fun setPinned(
        conversationId: String,
        isPinned: Boolean,
    )

    @Query("UPDATE conversations SET is_muted = :isMuted WHERE id = :conversationId")
    suspend fun setMuted(
        conversationId: String,
        isMuted: Boolean,
    )

    /**
     * Per-conversation disappearing timer, in milliseconds; null clears it and
     * the conversation falls back to the account-wide default.
     */
    @Query("UPDATE conversations SET disappearing_duration_ms = :durationMs WHERE id = :conversationId")
    suspend fun setDisappearingDuration(
        conversationId: String,
        durationMs: Long?,
    )

    @Query("UPDATE conversations SET draft_text = :draftText WHERE id = :conversationId")
    suspend fun setDraftText(
        conversationId: String,
        draftText: String?,
    )

    @Query("SELECT draft_text FROM conversations WHERE id = :conversationId")
    suspend fun draftText(conversationId: String): String?

    @Query("UPDATE conversations SET wallpaper = :wallpaper WHERE id = :conversationId")
    suspend fun setWallpaper(
        conversationId: String,
        wallpaper: String?,
    )

    /** All three flags in one statement, so a reader never sees half a policy. */
    @Query(
        "UPDATE conversations SET vault_auto_incoming = :autoIncoming, vault_view_once = :viewOnce, " +
            "vault_capture_protection = :captureProtection WHERE id = :conversationId",
    )
    suspend fun setVaultPolicy(
        conversationId: String,
        autoIncoming: Boolean,
        viewOnce: Boolean,
        captureProtection: Boolean,
    )

    @Query("UPDATE conversations SET is_hidden = :isHidden WHERE id = :conversationId")
    suspend fun setHidden(
        conversationId: String,
        isHidden: Boolean,
    )

    @Query("UPDATE conversations SET is_archived = :isArchived WHERE id = :conversationId")
    suspend fun setArchived(
        conversationId: String,
        isArchived: Boolean,
    )

    /**
     * The conversation's messages go with it by foreign key; their reactions
     * have no key to follow, so they are taken first.
     */
    @Transaction
    suspend fun deleteConversation(conversationId: String) {
        deleteReactionsInConversation(conversationId)
        // Nor does anything remember their ids, unless it is done here: a
        // message of a deleted chat, delivered again, must not come back.
        rememberDeletedInConversation(conversationId, System.currentTimeMillis())
        deleteConversationRow(conversationId)
    }

    @Query(
        "INSERT OR REPLACE INTO deleted_messages (id, deleted_at) SELECT id, :nowMillis FROM messages WHERE conversation_id = :conversationId",
    )
    suspend fun rememberDeletedInConversation(
        conversationId: String,
        nowMillis: Long,
    )

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversationRow(conversationId: String)

    @Query("DELETE FROM message_reactions WHERE message_id IN (SELECT id FROM messages WHERE conversation_id = :conversationId)")
    suspend fun deleteReactionsInConversation(conversationId: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()
}
