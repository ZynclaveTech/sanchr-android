package com.sanchr.domain.messaging

import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for message and conversation operations.
 * Implemented by the data layer; consumed by use cases.
 */
interface MessageRepository {
    /** Observes all non-archived conversations, ordered by most recent. */
    fun observeConversations(): Flow<List<Conversation>>

    /** Observes a single conversation and its lightweight participant metadata. */
    fun observeConversation(conversationId: String): Flow<Conversation?>

    /** Observes messages in a specific conversation. */
    fun observeMessages(conversationId: String): Flow<List<Message>>

    /**
     * Persists a new outbound message in the QUEUED state and returns the
     * row. The state machine (encrypt → RPC → transition) is owned by
     * [SendMessageUseCase] — this method is the persistence-only entry
     * point so the use case can insert once, then drive state transitions
     * through the other bookkeeping methods below.
     */
    suspend fun enqueueOutboundMessage(
        conversationId: String,
        content: String,
        contentType: String = "text",
    ): MessageEntity

    /**
     * Atomically bumps the `attempts` counter, stamps `last_attempt_at`,
     * and sets the status to [newStatus]. Returns the row's `attempts`
     * value *after* the increment so callers can decide whether to
     * transition to FAILED once the cap is reached.
     */
    suspend fun recordSendAttempt(
        messageId: String,
        newStatus: String,
    ): Int

    /**
     * On successful send, replaces the local client-generated id with the
     * server-assigned id, flips status to SENT, and adopts the server
     * timestamp — all in one UPDATE so observers see a single row
     * transition rather than a soft-delete + re-insert.
     */
    suspend fun adoptServerId(
        oldMessageId: String,
        newMessageId: String,
        serverTimestamp: Long,
    )

    /**
     * Marks a row FAILED (terminal) with a typed failure context so the UI
     * can render a distinct affordance per class (e.g. safety-number
     * warning for [FailureClass.UNTRUSTED_IDENTITY] vs generic error icon
     * for [FailureClass.NO_RECIPIENTS]). Called both when
     * [recordSendAttempt] reports the attempts cap was reached and when a
     * non-retriable condition (no recipients, untrusted identity) is
     * detected mid-attempt.
     *
     * @param failureReason Human-readable, stored for tooltip / long-press.
     * @param failureClass Canonical taxonomy — null only in legacy contexts.
     */
    suspend fun markSendFailed(
        messageId: String,
        failureReason: String?,
        failureClass: FailureClass?,
    )

    /**
     * Reverts a row's status to QUEUED after a transient failure so
     * `SendRetryWorker` can retry it. Does not touch the attempts counter —
     * that was bumped by [recordSendAttempt] at the start of the attempt.
     */
    suspend fun requeueAfterFailure(messageId: String)

    /**
     * Resolves the non-self participant set for [conversationId]. Used by
     * [SendMessageUseCase] to fan out encryption. Fails if no remote
     * participants are present — a conversation of one is a programming
     * error on the caller side.
     */
    suspend fun getOutboundRecipients(
        conversationId: String,
        selfUserId: String,
    ): List<String>

    /** Marks all messages in a conversation as read. */
    suspend fun markAsRead(conversationId: String)

    /** Deletes a message locally (and requests remote deletion if own message). */
    suspend fun deleteMessage(
        messageId: String,
        forEveryone: Boolean,
    )

    /** Fetches older messages for pagination. */
    suspend fun loadMoreMessages(
        conversationId: String,
        beforeTimestamp: Long,
        limit: Int,
    ): List<Message>

    /** Creates a new direct conversation with a user. */
    suspend fun createConversation(participantId: String): Conversation

    /**
     * Returns the conversation id for a direct chat with [peerUserId], creating
     * one if necessary. The server RPC is expected to be idempotent for
     * DIRECT conversations, so repeated calls with the same peer return the
     * same conversation id. Surfaced as a narrow helper so the new-chat UI
     * does not need to know about the underlying create-vs-fetch distinction.
     */
    suspend fun ensureConversation(peerUserId: String): String

    /** Pins or unpins a conversation. */
    suspend fun setPinned(
        conversationId: String,
        pinned: Boolean,
    )

    /** Archives or unarchives a conversation. */
    suspend fun setArchived(
        conversationId: String,
        archived: Boolean,
    )

    /**
     * Inserts a decrypted incoming message into local storage and stages a
     * pending ack. When [flushAckImmediately] is true (the default), the
     * pending ack batch is flushed to the server synchronously — that is the
     * right behavior for single-shot decrypt paths such as `RealtimeManager`.
     * Callers that drain many envelopes in a tight loop (e.g.
     * [com.sanchr.sync.MessageDrainWorker]) should pass `false` and call
     * [flushPendingAcks] once after the loop so the RPC count is O(1) in the
     * batch, not O(N) in envelopes.
     *
     * @param conversationId The conversation this message belongs to.
     * @param messageId The server-assigned message ID.
     * @param senderId The sender's user ID.
     * @param content The decrypted plaintext content.
     * @param contentType The message content type (e.g., "text", "image").
     * @param timestamp The server timestamp in epoch milliseconds.
     * @param flushAckImmediately If true, fires the ack RPC now; if false,
     *        stages a pending-ack row and defers the flush to the caller.
     */
    suspend fun insertDecryptedMessage(
        conversationId: String,
        messageId: String,
        senderId: String,
        content: String,
        contentType: String,
        timestamp: Long,
        flushAckImmediately: Boolean = true,
    )

    /**
     * Flushes any pending message acks to the server in a single batched RPC.
     * Safe to call when there are no pending rows (no-op). Exposed so batch
     * callers can decouple ack flushing from individual insert calls.
     */
    suspend fun flushPendingAcks()

    /**
     * Stages a pending ack for an envelope keyed by its own server-assigned
     * [conversationId] / [messageId] — the ids the server's delivery queue
     * actually tracks — regardless of any different id a decoded payload
     * may carry.
     *
     * A control payload (e.g. `receipt/v1`, `profile-key/v1`) is routed
     * away from [insertDecryptedMessage] entirely, since it must not be
     * persisted as a message, so without this call its envelope would
     * never be acked and the server would redeliver it indefinitely. Also
     * safe to call for an envelope that *was* persisted under a different
     * (payload-preferred) id: the extra ack entry this stages is either
     * identical to the one [insertDecryptedMessage] already staged (an
     * idempotent no-op) or, when the ids genuinely differ, an additional
     * harmless entry the server silently ignores.
     *
     * @param flushAckImmediately Same contract as [insertDecryptedMessage].
     */
    suspend fun ackEnvelope(
        conversationId: String,
        messageId: String,
        flushAckImmediately: Boolean = true,
    )
}
