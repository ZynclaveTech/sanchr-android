package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanchr.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query(
        """
        SELECT * FROM messages
        WHERE conversation_id = :conversationId AND is_deleted = 0
        ORDER BY timestamp ASC
        """,
    )
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE conversation_id = :conversationId AND is_deleted = 0
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getMessagesPaginated(
        conversationId: String,
        limit: Int,
        offset: Int,
    ): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(
        messageId: String,
        status: String,
    )

    /**
     * Terminal-state transition for outbound sends that cannot be retried.
     * Writes `status`, `failure_reason`, and `failure_class` in one UPDATE so
     * observers see a single row transition and the UI never sees a
     * `FAILED` row with a stale (null) failure context.
     */
    @Query(
        """
        UPDATE messages
        SET status = :status,
            failure_reason = :failureReason,
            failure_class = :failureClass
        WHERE id = :messageId
        """,
    )
    suspend fun updateMessageFailure(
        messageId: String,
        status: String,
        failureReason: String?,
        failureClass: String?,
    )

    /**
     * Marks an attempt on a QUEUED row. Bumps `attempts`, stamps
     * `last_attempt_at`, and optionally transitions status (e.g. to FAILED
     * once attempts cap is reached). Returns the number of rows affected so
     * callers can detect a race with a concurrent in-flight send.
     */
    @Query(
        """
        UPDATE messages
        SET attempts = attempts + 1,
            last_attempt_at = :attemptedAt,
            status = :newStatus
        WHERE id = :messageId
        """,
    )
    suspend fun recordSendAttempt(
        messageId: String,
        attemptedAt: Long,
        newStatus: String,
    ): Int

    /**
     * Rows waiting to be (re)sent: status = QUEUED, attempts below cap, and
     * either never attempted or their backoff window has elapsed. Ordered by
     * oldest-first so a burst drain fires in insertion order.
     */
    @Query(
        """
        SELECT * FROM messages
        WHERE status = 'QUEUED'
          AND attempts < :maxAttempts
          AND (last_attempt_at IS NULL OR last_attempt_at + :minBackoffMillis <= :now)
          AND is_deleted = 0
        ORDER BY timestamp ASC
        LIMIT :limit
        """,
    )
    suspend fun getRetryableQueued(
        now: Long,
        maxAttempts: Int,
        minBackoffMillis: Long,
        limit: Int,
    ): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE status = 'QUEUED' AND is_deleted = 0")
    suspend fun countQueued(): Int

    @Query("UPDATE messages SET is_deleted = 1 WHERE id = :messageId")
    suspend fun softDeleteMessage(messageId: String)

    /** Swaps a row's content in place, keeping id, sender and timestamp (a view-once tombstone). */
    @Query("UPDATE messages SET content_type = :contentType, content_body = :contentBody WHERE id = :messageId")
    suspend fun replaceContent(
        messageId: String,
        contentType: String,
        contentBody: String,
    )

    /**
     * In-place swap of a locally-generated client message id for the server
     * id once `SendMessage` / `SendSealedMessage` returns. Also flips the
     * status (typically QUEUED/SENDING → SENT) and adopts the server
     * timestamp in a single UPDATE so observers see one row transition, not
     * a delete + insert.
     *
     * Row identity is preserved — messages has no FK inbound, so mutating
     * the PK is safe. Returns the number of rows affected so callers can
     * detect a race where the row was already moved (or deleted).
     */
    @Query(
        """
        UPDATE messages
        SET id = :newId,
            status = :newStatus,
            timestamp = :serverTimestamp
        WHERE id = :oldId
        """,
    )
    suspend fun adoptServerMessageId(
        oldId: String,
        newId: String,
        newStatus: String,
        serverTimestamp: Long,
    ): Int

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteAllMessagesInConversation(conversationId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query(
        """
        DELETE FROM messages
        WHERE expires_at IS NOT NULL AND expires_at < :currentTimeMillis
        """,
    )
    suspend fun deleteExpiredMessages(currentTimeMillis: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE conversation_id = :conversationId AND status != 'READ'")
    fun observeUnreadCount(conversationId: String): Flow<Int>

    /**
     * Observes incoming (non-self) messages inserted after [timestamp] epoch
     * millis. Used by `NewMessageNotifier` in `core:notifications` to render
     * a system notification for each freshly-delivered message without
     * reading any content from the FCM payload.
     *
     * The query filters by `sender_id != :selfUserId` so the observer does
     * not fire for the user's own outbound rows landing in the DB, and by
     * `timestamp > :afterTimestamp` so notifications only fire for messages
     * newer than when the notifier started (no catch-up floods across a
     * process restart).
     *
     * Room turns this into a Flow that re-emits on every insert/update —
     * the notifier applies its own de-dupe by message id.
     */
    @Query(
        """
        SELECT * FROM messages
        WHERE timestamp > :afterTimestamp
          AND sender_id != :selfUserId
          AND is_deleted = 0
        ORDER BY timestamp ASC
        """,
    )
    fun observeIncomingMessagesAfter(
        afterTimestamp: Long,
        selfUserId: String,
    ): Flow<List<MessageEntity>>
}
