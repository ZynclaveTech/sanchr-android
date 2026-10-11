package com.sanchr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sanchr.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    /**
     * The newest [limit] messages, oldest first.
     *
     * A window, because the unbounded observer this replaced streamed an
     * entire conversation into memory and into composition on every change —
     * the cost iOS avoids by bounding its transcript. The inner query takes
     * the newest rows; the outer one puts them back in reading order.
     */
    @Query(
        """
        SELECT * FROM (
            SELECT * FROM messages
            WHERE conversation_id = :conversationId AND is_deleted = 0
            ORDER BY timestamp DESC
            LIMIT :limit
        ) ORDER BY timestamp ASC
        """,
    )
    fun observeRecentMessages(
        conversationId: String,
        limit: Int,
    ): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    /**
     * Ids of this conversation's text messages containing [needle], newest
     * first (iOS `searchMessages`: a LIKE over the body, capped at 50).
     * [needle] is matched as a substring; callers escape `%` and `_`.
     */
    @Query(
        """
        SELECT id FROM messages
        WHERE conversation_id = :conversationId AND is_deleted = 0 AND content_type = 'text'
          AND content_body LIKE '%' || :needle || '%' ESCAPE '\'
        ORDER BY timestamp DESC
        LIMIT 50
        """,
    )
    suspend fun searchTextMessageIds(
        conversationId: String,
        needle: String,
    ): List<String>

    /** The newest non-deleted message of every conversation (ties on timestamp may yield more than one row per chat). */
    @Query(
        """
        SELECT m.* FROM messages m
        WHERE m.is_deleted = 0
          AND m.timestamp = (
            SELECT MAX(timestamp) FROM messages
            WHERE conversation_id = m.conversation_id AND is_deleted = 0
          )
        """,
    )
    fun observeLatestPerConversation(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    /**
     * Saves a message only if no message has its id yet.
     *
     * For anything that arrives from someone else. The id of a received
     * message is chosen by its sender, and [insertMessage] overwrites: a
     * sender who reused an id — and they know the id of every message in the
     * chat they share with this user, in both directions — replaced that
     * message, whoever had written it.
     *
     * @return the new row's id, or -1 if a message with this id was already
     *         here and nothing was written.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessageIfAbsent(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(
        messageId: String,
        status: String,
    )

    /**
     * Moves one of [ownUserId]'s messages to [newStatus], if it is in one of
     * [from]. For receipts: one statement, so "is it mine, and is this a step
     * forward" cannot be true when checked and false when written.
     *
     * @return rows changed: 1, or 0 if the message is not here, not this
     *         user's, or not in a status the move is allowed from.
     */
    @Query("UPDATE messages SET status = :newStatus WHERE id = :messageId AND sender_id = :ownUserId AND status IN (:from)")
    suspend fun advanceOwnMessageStatus(
        messageId: String,
        ownUserId: String,
        newStatus: String,
        from: List<String>,
    ): Int

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

    /**
     * "Delete message": the row stays, flagged, and its reactions go.
     *
     * Every delete here takes the reactions first, in one transaction.
     * `message_reactions` has no foreign key to do it (a reaction may arrive
     * before its message), and a delete trigger cannot: Room turns recursive
     * triggers on, so re-saving a message with `REPLACE` would fire it and
     * strip the reactions from a message that is still there.
     */
    @Transaction
    suspend fun softDeleteMessage(messageId: String) {
        deleteReactionsOn(messageId)
        flagDeleted(messageId)
    }

    @Query("UPDATE messages SET is_deleted = 1 WHERE id = :messageId")
    suspend fun flagDeleted(messageId: String)

    @Query("DELETE FROM message_reactions WHERE message_id = :messageId")
    suspend fun deleteReactionsOn(messageId: String)

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

    @Transaction
    suspend fun deleteAllMessagesInConversation(conversationId: String) {
        deleteReactionsInConversation(conversationId)
        deleteMessageRowsInConversation(conversationId)
    }

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteMessageRowsInConversation(conversationId: String)

    @Query("DELETE FROM message_reactions WHERE message_id IN (SELECT id FROM messages WHERE conversation_id = :conversationId)")
    suspend fun deleteReactionsInConversation(conversationId: String)

    /** Every message a conversation holds, by id. For clearing a chat: its media has to be found before its rows go. */
    @Query("SELECT id FROM messages WHERE conversation_id = :conversationId")
    suspend fun messageIdsInConversation(conversationId: String): List<String>

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    /** @return how many messages expired. */
    @Transaction
    suspend fun deleteExpiredMessages(currentTimeMillis: Long): Int {
        deleteReactionsOnExpiredMessages(currentTimeMillis)
        return deleteExpiredMessageRows(currentTimeMillis)
    }

    @Query(
        """
        DELETE FROM messages
        WHERE expires_at IS NOT NULL AND expires_at < :currentTimeMillis
        """,
    )
    suspend fun deleteExpiredMessageRows(currentTimeMillis: Long): Int

    @Query(
        """
        DELETE FROM message_reactions
        WHERE message_id IN (SELECT id FROM messages WHERE expires_at IS NOT NULL AND expires_at < :currentTimeMillis)
        """,
    )
    suspend fun deleteReactionsOnExpiredMessages(currentTimeMillis: Long)

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
