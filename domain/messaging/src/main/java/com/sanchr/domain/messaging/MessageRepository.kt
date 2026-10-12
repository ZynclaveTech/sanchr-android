package com.sanchr.domain.messaging

import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.model.ChatVaultPolicy
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageReaction
import com.sanchr.core.model.MessageStatus
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

    /**
     * The newest [limit] messages of a conversation, oldest first.
     *
     * A window rather than the whole transcript: the unbounded observer
     * streams every message a conversation has ever had into memory and into
     * composition on each change.
     */
    fun observeRecentMessages(
        conversationId: String,
        limit: Int,
    ): Flow<List<Message>>

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
        /**
         * Epoch millis at which the sender's own copy self-destructs, or null
         * for no timer. Resolved by [SendMessageUseCase]; the recipient's copy
         * is governed by the timer stamped into the envelope, not by this.
         */
        expiresAtMillis: Long? = null,
        /** The message this one quotes, or null. Rides the envelope as `reply_to_message_id`. */
        replyToId: String? = null,
    ): MessageEntity

    /**
     * This conversation's own disappearing-message timer in seconds, or null
     * when it sets none (in which case callers fall back to the account-wide
     * default). Mirrors iOS, which reads the timer from the conversation row
     * rather than from a global preference.
     */
    suspend fun conversationDisappearingSeconds(conversationId: String): Long?

    /**
     * Whether messages from [senderId] must be dropped.
     *
     * The server enforces blocking keyed on the sender, which it knows only
     * for the standard path. A sealed message carries no sender the server
     * can read — that is the point of sealed sending — so for 1:1 chats,
     * which use it whenever available, the client is the only place left
     * that can apply the block.
     *
     * An unknown sender is not blocked.
     */
    suspend fun isSenderBlocked(senderId: String): Boolean

    /**
     * Makes sure [conversationId] is on this phone before a message for it
     * is saved.
     *
     * A message cannot be stored without its conversation, and a
     * conversation only reached this phone with the periodic sync. So the
     * first message of a chat this phone had not seen yet — which is what
     * someone new writing for the first time is — could not be saved, and
     * was lost: its envelope cannot be decrypted a second time.
     *
     * The server's copy is taken if it can be had. Failing that, a
     * placeholder holding this user and the sender, which the next sync
     * fills in. Nothing is created for a message deleted from this phone on
     * purpose ([messageId]): it is about to be turned away, and its chat
     * must not come back to hold nothing.
     */
    suspend fun prepareConversationForIncoming(
        conversationId: String,
        messageId: String,
        senderId: String,
        timestampMillis: Long,
    )

    /**
     * Whether a message from [senderId] may be filed in [conversationId] —
     * see [DirectChatMembership] for the rule and for why it says yes to
     * anything it cannot be sure about.
     */
    suspend fun mayPostTo(
        conversationId: String,
        senderId: String,
    ): Boolean

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
     * The stored row for [messageId], or null when there is none.
     *
     * Needed to retry a failed send: the attempt runs from the row, and
     * nothing could read one back, so a message that failed could not be
     * resent at all.
     */
    suspend fun outboundMessage(messageId: String): MessageEntity?

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

    /**
     * Resolves the single other participant of a DIRECT conversation, for
     * [SendReadReceiptUseCase] — a read receipt is 1:1 only. Returns null
     * when [conversationId] does not exist, is a GROUP, or (anything else
     * unexpected) does not have exactly one other participant. Mirrors
     * iOS's `oneToOneReceiptPeerId`.
     *
     * Deliberately narrower than [getOutboundRecipients], which throws on
     * zero remote participants and does not expose the conversation's
     * type — neither behaviour suits a receipt, which must silently do
     * nothing for a group or a conversation it can no longer resolve.
     */
    suspend fun oneToOneRecipient(
        conversationId: String,
        selfUserId: String,
    ): String?

    /**
     * Applies a receipt that [fromUserId] sent, sealed, to [messageId].
     *
     * Only what a receipt can truthfully say is written — see [ReceiptRule]:
     * the message must be one this user sent, [fromUserId] must be someone
     * else who belongs to its conversation, and [status] must move it
     * forward to delivered or read. Anything else is ignored.
     *
     * Not guaranteed never to throw (a Room/SQLite failure is not
     * swallowed). [ReceiveMessageUseCase], the only caller, guards its own
     * call so a write failure can never leave an envelope unacked.
     *
     * @return whether the message's status changed.
     */
    suspend fun applyReceiptStatus(
        messageId: String,
        status: MessageStatus,
        fromUserId: String,
    ): Boolean

    /** Adds or removes one user's emoji on a message; a no-op when the row already matches. */
    suspend fun applyReaction(
        messageId: String,
        userId: String,
        emoji: String,
        removed: Boolean,
        timestampMillis: Long,
    )

    suspend fun reactionsFor(messageId: String): List<MessageReaction>

    /** Deletes a message locally (and requests remote deletion if own message). */
    suspend fun deleteMessage(
        messageId: String,
        forEveryone: Boolean,
    )

    /**
     * Replaces opened view-once media with a "Viewed" tombstone in place (same
     * id and timestamp) and asks the server to drop the message, best effort,
     * as iOS `deleteViewOnceMessage`. The cached bytes are the caller's to wipe first.
     */
    suspend fun tombstoneViewOnce(messageId: String)

    /** Ids of text messages in [conversationId] containing [query] (case-insensitive substring), newest first, at most 50. */
    suspend fun searchMessages(
        conversationId: String,
        query: String,
    ): List<String>

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

    /** Archived conversations, newest message first, with their newest message attached. */
    fun observeArchivedConversations(): Flow<List<Conversation>>

    /**
     * Hides a conversation from every chat list on this device, or restores it.
     *
     * Local only: nothing is told to the server and nothing is deleted, so the
     * transcript is intact when the chat is restored.
     */
    suspend fun setHidden(
        conversationId: String,
        hidden: Boolean,
    )

    /** Hidden conversations, newest first, with their newest message attached. */
    fun observeHiddenConversations(): Flow<List<Conversation>>

    /**
     * Sets how long messages sent in this conversation survive, or clears the
     * timer with null.
     *
     * The send path has always read this per-conversation value; until now
     * nothing could write it, so every chat fell back to the global default.
     */
    suspend fun setDisappearingDuration(
        conversationId: String,
        durationMs: Long?,
    )

    /**
     * Unsent composer text for this chat, or null when there is none.
     *
     * Kept in the encrypted database with the messages: a draft is message
     * content, so it does not belong in plaintext preferences. Deleting the
     * conversation takes it with it.
     */
    suspend fun draftText(conversationId: String): String?

    suspend fun setDraftText(
        conversationId: String,
        text: String?,
    )

    /**
     * Sets this chat's wallpaper, or clears it with null so the chat follows
     * the account-wide choice again.
     */
    suspend fun setWallpaper(
        conversationId: String,
        wallpaper: String?,
    )

    /** Replaces this chat's vault policy. Device-only; nothing is sent. */
    suspend fun setVaultPolicy(
        conversationId: String,
        policy: ChatVaultPolicy,
    )

    /** This chat's vault policy as stored now; all off for a chat that has none or is unknown. */
    suspend fun vaultPolicy(conversationId: String): ChatVaultPolicy

    /** One stored message, decoded, or null when it is not on this device. */
    suspend fun message(messageId: String): Message?

    /**
     * Replaces a received message with the marker Auto-Vault Incoming leaves
     * behind. Local only: the sender's copy and the server's are untouched.
     */
    suspend fun tombstoneAutoVaulted(messageId: String)

    /** Mutes or unmutes notifications for a conversation: tells the server, then the local row. */
    suspend fun setMuted(
        conversationId: String,
        muted: Boolean,
    )

    /** The ids of every disappearing message whose deadline is at or before [nowMillis]. */
    suspend fun expiredMessageIds(nowMillis: Long): List<String>

    /**
     * Removes every disappearing message whose deadline is at or before
     * [nowMillis]. Rows only — see [ExpireMessagesUseCase] for the files.
     *
     * @return how many were removed.
     */
    suspend fun deleteExpiredMessages(nowMillis: Long): Int

    /** The earliest deadline among the disappearing messages held, or null; re-emits as messages come and go. */
    fun observeNextExpiryMillis(): Flow<Long?>

    /**
     * Finishes deleting messages that were deleted before deleting erased
     * anything: empties what they said and removes their cached media.
     * Cheap when there is nothing to do, which is every time but the first.
     *
     * @return how many were emptied.
     */
    suspend fun eraseDeletedMessages(): Int

    /** Which of [ids] are messages on this phone that can still have media in the cache. */
    suspend fun messageIdsHoldingMedia(ids: List<String>): Set<String>

    /** The ids of every message this device holds for a conversation. */
    suspend fun messageIds(conversationId: String): List<String>

    /**
     * Removes every message of a conversation from this device, leaving the
     * conversation itself. Local only: nothing is asked of the server or of
     * the other person, whose copies are theirs.
     */
    suspend fun clearMessages(conversationId: String)

    /**
     * Deletes a conversation and its messages here, and asks the server to
     * drop it; a server failure still removes it locally (iOS does the same
     * and re-settles on the next sync).
     */
    suspend fun deleteConversation(conversationId: String)

    /**
     * Inserts a decrypted incoming message into local storage and, by
     * default, stages a pending ack keyed on this same [conversationId] /
     * [messageId]. When [flushAckImmediately] is true (the default), the
     * pending ack batch is flushed to the server synchronously — that is the
     * right behavior for single-shot decrypt paths such as `RealtimeManager`.
     * Callers that drain many envelopes in a tight loop (e.g.
     * [com.sanchr.sync.MessageDrainWorker]) should pass `false` and call
     * [flushPendingAcks] once after the loop so the RPC count is O(1) in the
     * batch, not O(N) in envelopes.
     *
     * @param conversationId The conversation this message belongs to. May be
     *        a payload-preferred id rather than the envelope's own — see
     *        [stageAck].
     * @param messageId The message ID this row is keyed on. Same caveat as
     *        [conversationId].
     * @param senderId The sender's user ID.
     * @param content The decrypted plaintext content.
     * @param contentType The message content type (e.g., "text", "image").
     * @param timestamp The server timestamp in epoch milliseconds.
     * @param flushAckImmediately If true, fires the ack RPC now; if false,
     *        stages a pending-ack row (when [stageAck] is true) and defers
     *        the flush to the caller.
     * @param stageAck Whether this call should also stage a pending ack
     *        under [conversationId] / [messageId]. Defaults to `true` — the
     *        pre-existing behavior every caller but [ReceiveMessageUseCase]
     *        relies on. [ReceiveMessageUseCase] passes `false` because it
     *        stages the *envelope's* ack itself via [ackEnvelope]: this
     *        row's id can be a payload-preferred id the server's delivery
     *        queue does not recognize (see [ackEnvelope]'s doc), so staging
     *        an ack here too would just be a second, server-rejected entry
     *        for no benefit.
     */
    suspend fun insertDecryptedMessage(
        conversationId: String,
        messageId: String,
        senderId: String,
        content: String,
        contentType: String,
        timestamp: Long,
        flushAckImmediately: Boolean = true,
        stageAck: Boolean = true,
        /**
         * Epoch millis at which this message self-destructs, or null when the
         * sender set no timer. Already resolved by the caller against the
         * server timestamp — see [ReceiveMessageUseCase] for why the deadline
         * is not anchored to local arrival time.
         */
        expiresAtMillis: Long? = null,
        /** The message the sender quoted, or null. */
        replyToId: String? = null,
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
     * This is the *only* place an envelope's ack gets staged once
     * [ReceiveMessageUseCase] routes a payload: a control payload (e.g.
     * `receipt/v1`, `profile-key/v1`) is routed away from
     * [insertDecryptedMessage] entirely, since it must not be persisted as
     * a message, so without this call its envelope would never be acked
     * and the server would redeliver it indefinitely. For a
     * [RoutedPayload.UserMessage] persisted under a payload-preferred id,
     * [ReceiveMessageUseCase] passes `stageAck = false` to
     * [insertDecryptedMessage] and relies on this call instead — otherwise
     * the row's own (possibly payload-keyed) ack staging would produce a
     * second entry the server silently rejects on every such message
     * (a payload id is a random v4 UUID; the server's delivery queue only
     * recognizes its own v1 timeuuid), which is needless load and log
     * noise this method exists to avoid.
     *
     * @param flushAckImmediately Same contract as [insertDecryptedMessage].
     */
    suspend fun ackEnvelope(
        conversationId: String,
        messageId: String,
        flushAckImmediately: Boolean = true,
    )
}
