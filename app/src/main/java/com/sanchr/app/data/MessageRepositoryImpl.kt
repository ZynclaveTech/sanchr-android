package com.sanchr.app.data

import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.ConversationType
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.core.model.MessageStatus
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.proto.messaging.DeleteMessageRequest
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.SendMessageRequest
import com.sanchr.proto.messaging.StartDirectConversationRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import org.json.JSONArray
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.sanchr.proto.messaging.Conversation as ProtoConversation

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messagingClient: MessagingServiceClient,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
) : MessageRepository {

    override fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeConversations().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        messageDao.observeMessages(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun sendMessage(conversationId: String, content: String): Message {
        val clientMessageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Insert optimistic local message
        val entity = MessageEntity(
            id = clientMessageId,
            conversationId = conversationId,
            senderId = "", // Will be filled by server context / current user
            contentType = "text",
            contentBody = content,
            status = MessageStatus.SENDING.name,
            timestamp = now,
        )
        messageDao.insertMessage(entity)

        val response = messagingClient.sendMessage(
            SendMessageRequest(
                conversationId = conversationId,
                contentType = "text",
                clientMessageId = clientMessageId,
                timestamp = now,
            ),
        )

        // Update with server-assigned ID and SENT status
        val updatedEntity = entity.copy(
            id = response.serverMessageId,
            status = MessageStatus.SENT.name,
            timestamp = response.timestamp,
        )
        messageDao.softDeleteMessage(clientMessageId) // remove optimistic entry
        messageDao.insertMessage(updatedEntity)

        return updatedEntity.toDomain()
    }

    override suspend fun markAsRead(conversationId: String) {
        conversationDao.markAsRead(conversationId)
    }

    override suspend fun deleteMessage(messageId: String, forEveryone: Boolean) {
        messageDao.softDeleteMessage(messageId)

        if (forEveryone) {
            messagingClient.deleteMessage(
                DeleteMessageRequest(
                    messageId = messageId,
                    forEveryone = true,
                ),
            )
        }
    }

    override suspend fun loadMoreMessages(
        conversationId: String,
        beforeTimestamp: Long,
        limit: Int,
    ): List<Message> {
        // DAO uses limit+offset pagination; offset 0 for simplicity — caller manages externally
        val entities = messageDao.getMessagesPaginated(
            conversationId = conversationId,
            limit = limit,
            offset = 0,
        )
        return entities.map { it.toDomain() }
    }

    override suspend fun createConversation(participantId: String): Conversation {
        val protoConv = messagingClient.startDirectConversation(
            StartDirectConversationRequest(recipientUserId = participantId),
        )
        val entity = protoConv.toEntity()
        conversationDao.insertConversation(entity)
        return entity.toDomain()
    }

    override suspend fun setPinned(conversationId: String, pinned: Boolean) {
        conversationDao.setPinned(conversationId, pinned)
    }

    override suspend fun setArchived(conversationId: String, archived: Boolean) {
        conversationDao.setArchived(conversationId, archived)
    }

    override suspend fun insertDecryptedMessage(
        conversationId: String,
        messageId: String,
        senderId: String,
        content: String,
        contentType: String,
        timestamp: Long,
    ) {
        val entity = MessageEntity(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            contentType = contentType,
            contentBody = content,
            status = MessageStatus.DELIVERED.name,
            timestamp = timestamp,
        )
        messageDao.insertMessage(entity)
    }

    // ── Mapping helpers ──

    private fun MessageEntity.toDomain(): Message = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        content = contentType.toMessageContent(contentBody),
        status = MessageStatus.valueOf(status),
        timestamp = Instant.fromEpochMilliseconds(timestamp),
        editedAt = editedAt?.let { Instant.fromEpochMilliseconds(it) },
        replyToId = replyToId,
        expiresAt = expiresAt?.let { Instant.fromEpochMilliseconds(it) },
    )

    private fun String.toMessageContent(body: String): MessageContent = when (this) {
        "text" -> MessageContent.Text(body)
        else -> MessageContent.Text(body) // Fallback; richer types handled when needed
    }

    private fun ConversationEntity.toDomain(): Conversation {
        return Conversation(
            id = id,
            type = try { ConversationType.valueOf(type) } catch (_: Exception) { ConversationType.DIRECT },
            participants = emptyList(), // Participants require User lookup — kept lightweight
            title = title,
            avatarUrl = avatarUrl,
            lastMessage = null, // Loaded separately via observeMessages
            unreadCount = unreadCount,
            isPinned = isPinned,
            isMuted = isMuted,
            isArchived = isArchived,
            disappearingMessageDuration = disappearingDurationMs,
            updatedAt = Instant.fromEpochMilliseconds(updatedAt),
            createdAt = Instant.fromEpochMilliseconds(createdAt),
        )
    }

    private fun ProtoConversation.toEntity(): ConversationEntity = ConversationEntity(
        id = id,
        type = type.uppercase(),
        title = title.ifEmpty { null },
        avatarUrl = avatarUrl.ifEmpty { null },
        participantIds = JSONArray(participantIds).toString(),
        lastMessagePreview = lastMessagePreview.ifEmpty { null },
        lastMessageTimestamp = lastMessageTimestamp.takeIf { it > 0 },
        unreadCount = unreadCount,
        isPinned = isPinned,
        isMuted = isMuted,
        updatedAt = updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        createdAt = createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
    )
}
