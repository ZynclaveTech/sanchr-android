package com.sanchr.domain.messaging

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

    /** Sends an encrypted message to a conversation. */
    suspend fun sendMessage(
        conversationId: String,
        content: String,
    ): Message

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
     * Inserts a decrypted incoming message into local storage.
     * Called after successfully decrypting an [EncryptedEnvelope] from the server.
     *
     * @param conversationId The conversation this message belongs to.
     * @param messageId The server-assigned message ID.
     * @param senderId The sender's user ID.
     * @param content The decrypted plaintext content.
     * @param contentType The message content type (e.g., "text", "image").
     * @param timestamp The server timestamp in epoch milliseconds.
     */
    suspend fun insertDecryptedMessage(
        conversationId: String,
        messageId: String,
        senderId: String,
        content: String,
        contentType: String,
        timestamp: Long,
    )
}
