package com.sanchr.app.data

import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.dao.PendingMessageAckDao
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.database.entity.PendingMessageAckEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.ConversationType
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.core.model.MessageStatus
import com.sanchr.core.model.User
import com.sanchr.domain.messaging.FailureClass
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.proto.messaging.Conversation as ProtoConversation
import com.sanchr.proto.messaging.DeleteMessageRequest
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.StartDirectConversationRequest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class MessageRepositoryImpl
    @Inject
    constructor(
        private val messagingClient: MessagingServiceClient,
        private val messageDao: MessageDao,
        private val conversationDao: ConversationDao,
        private val pendingMessageAckDao: PendingMessageAckDao,
        private val sessionManager: SessionManager,
    ) : MessageRepository {
        override fun observeConversations(): Flow<List<Conversation>> =
            conversationDao.observeConversations().map { entities ->
                entities.map { it.toDomain() }
            }

        override fun observeConversation(conversationId: String): Flow<Conversation?> =
            conversationDao.observeConversation(conversationId).map { entity ->
                entity?.toDomain()
            }

        override fun observeMessages(conversationId: String): Flow<List<Message>> =
            messageDao.observeMessages(conversationId).map { entities ->
                entities.map { it.toDomain() }
            }

        override suspend fun enqueueOutboundMessage(
            conversationId: String,
            content: String,
            contentType: String,
        ): MessageEntity {
            val currentUserId = sessionManager.getUserId().orEmpty()
            require(currentUserId.isNotBlank()) { "Missing current user id" }

            val entity =
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    senderId = currentUserId,
                    contentType = contentType,
                    contentBody = content,
                    status = MessageStatus.QUEUED.name,
                    timestamp = System.currentTimeMillis(),
                )
            messageDao.insertMessage(entity)
            return entity
        }

        override suspend fun recordSendAttempt(
            messageId: String,
            newStatus: String,
        ): Int {
            messageDao.recordSendAttempt(
                messageId = messageId,
                attemptedAt = System.currentTimeMillis(),
                newStatus = newStatus,
            )
            // Re-read to return the post-increment attempts count. The two
            // statements run on the same dispatcher so the UPDATE is
            // visible here; wrapping in @Transaction is unnecessary since
            // no concurrent writer touches this row mid-attempt (the use
            // case serializes transitions for a given message id).
            return messageDao.getMessageById(messageId)?.attempts ?: 0
        }

        override suspend fun adoptServerId(
            oldMessageId: String,
            newMessageId: String,
            serverTimestamp: Long,
        ) {
            messageDao.adoptServerMessageId(
                oldId = oldMessageId,
                newId = newMessageId,
                newStatus = MessageStatus.SENT.name,
                serverTimestamp = serverTimestamp,
            )
        }

        override suspend fun markSendFailed(
            messageId: String,
            failureReason: String?,
            failureClass: FailureClass?,
        ) {
            messageDao.updateMessageFailure(
                messageId = messageId,
                status = MessageStatus.FAILED.name,
                failureReason = failureReason,
                failureClass = failureClass?.name,
            )
        }

        override suspend fun requeueAfterFailure(messageId: String) {
            messageDao.updateMessageStatus(messageId, MessageStatus.QUEUED.name)
        }

        override suspend fun getOutboundRecipients(
            conversationId: String,
            selfUserId: String,
        ): List<String> {
            val conversation =
                conversationDao.getConversationById(conversationId)
                    ?: error("Conversation $conversationId not found")
            val recipients =
                parseParticipantIds(conversation.participantIds)
                    .filter { it != selfUserId }
            require(recipients.isNotEmpty()) { "Conversation has no remote participants" }
            return recipients
        }

        override suspend fun markAsRead(conversationId: String) {
            conversationDao.markAsRead(conversationId)
        }

        override suspend fun deleteMessage(
            messageId: String,
            forEveryone: Boolean,
        ) {
            messageDao.softDeleteMessage(messageId)

            if (forEveryone) {
                messagingClient.deleteMessage(
                    DeleteMessageRequest(
                        conversationId = messageDao.getMessageById(messageId)?.conversationId.orEmpty(),
                        messageId = messageId,
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
            val entities =
                messageDao.getMessagesPaginated(
                    conversationId = conversationId,
                    limit = limit,
                    offset = 0,
                )
            return entities.map { it.toDomain() }
        }

        override suspend fun createConversation(participantId: String): Conversation {
            val protoConv =
                messagingClient.startDirectConversation(
                    StartDirectConversationRequest(recipientId = participantId),
                )
            val entity = protoConv.toEntity()
            conversationDao.insertConversation(entity)
            return entity.toDomain()
        }

        override suspend fun ensureConversation(peerUserId: String): String {
            // Server-side startDirectConversation is get-or-create: if a DIRECT
            // conversation already exists between these two users it returns
            // that existing row. The local REPLACE-on-insert keeps Room in
            // sync. This keeps the UI layer ignorant of the distinction.
            return createConversation(peerUserId).id
        }

        override suspend fun setPinned(
            conversationId: String,
            pinned: Boolean,
        ) {
            conversationDao.setPinned(conversationId, pinned)
        }

        override suspend fun setArchived(
            conversationId: String,
            archived: Boolean,
        ) {
            conversationDao.setArchived(conversationId, archived)
        }

        override suspend fun insertDecryptedMessage(
            conversationId: String,
            messageId: String,
            senderId: String,
            content: String,
            contentType: String,
            timestamp: Long,
            flushAckImmediately: Boolean,
        ) {
            val entity =
                MessageEntity(
                    id = messageId,
                    conversationId = conversationId,
                    senderId = senderId,
                    contentType = contentType,
                    contentBody = content,
                    status = MessageStatus.DELIVERED.name,
                    timestamp = timestamp,
                )
            messageDao.insertMessage(entity)
            pendingMessageAckDao.insertAck(
                PendingMessageAckEntity(
                    conversationId = conversationId,
                    messageId = messageId,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            if (flushAckImmediately) flushPendingAcks()
        }

        override suspend fun flushPendingAcks() {
            val pendingAcks = pendingMessageAckDao.getPendingAcks(limit = 100)
            if (pendingAcks.isEmpty()) return

            messagingClient.ackMessages(
                com.sanchr.proto.messaging.AckMessagesRequest(
                    messages =
                        pendingAcks.map { ack ->
                            com.sanchr.proto.messaging.AckedMessageRef(
                                conversationId = ack.conversationId,
                                messageId = ack.messageId,
                            )
                        },
                ),
            )

            pendingAcks.forEach { ack ->
                pendingMessageAckDao.deleteAck(
                    conversationId = ack.conversationId,
                    messageId = ack.messageId,
                )
            }
        }

        // ── Mapping helpers ──

        private fun MessageEntity.toDomain(): Message =
            Message(
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

        private fun String.toMessageContent(body: String): MessageContent =
            when (this) {
                "text" -> MessageContent.Text(body)
                "image", "video" ->
                    parseMediaContent(body)?.let { payload ->
                        MessageContent.Image(
                            url = payload.url,
                            thumbnailUrl = payload.thumbnailUrl,
                            width = payload.width,
                            height = payload.height,
                            caption = payload.caption,
                        )
                    } ?: MessageContent.Text(body)
                "voice", "audio" ->
                    parseMediaContent(body)?.let { payload ->
                        MessageContent.Voice(
                            url = payload.url,
                            durationMs = payload.durationMs,
                        )
                    } ?: MessageContent.Text("[Voice message]")
                "file", "document" ->
                    parseMediaContent(body)?.let { payload ->
                        MessageContent.File(
                            url = payload.url,
                            fileName = payload.fileName ?: payload.url.substringAfterLast('/'),
                            mimeType = payload.mimeType ?: "application/octet-stream",
                            sizeBytes = payload.sizeBytes,
                        )
                    } ?: MessageContent.Text(body)
                "location" ->
                    runCatching {
                        val json = JSONObject(body)
                        MessageContent.Location(
                            latitude = json.getDouble("latitude"),
                            longitude = json.getDouble("longitude"),
                            label = json.optString("label").takeIf { it.isNotBlank() },
                        )
                    }.getOrElse { MessageContent.Text("[Location]") }
                else -> MessageContent.Text(body) // Fallback; richer types handled when needed
            }

        private data class MediaPayload(
            val url: String,
            val thumbnailUrl: String?,
            val mimeType: String?,
            val sizeBytes: Long,
            val caption: String?,
            val width: Int,
            val height: Int,
            val durationMs: Long,
            val fileName: String?,
        )

        private fun parseMediaContent(body: String): MediaPayload? =
            runCatching {
                val json = JSONObject(body)
                MediaPayload(
                    url = json.getString("url"),
                    thumbnailUrl = json.optString("thumbnailURL").takeIf { it.isNotBlank() },
                    mimeType = json.optString("mimeType").takeIf { it.isNotBlank() },
                    sizeBytes = json.optLong("sizeBytes", 0L),
                    caption = json.optString("caption").takeIf { it.isNotBlank() },
                    width = json.optInt("width", 0),
                    height = json.optInt("height", 0),
                    durationMs = json.optLong("durationMs", 0L),
                    fileName = json.optString("fileName").takeIf { it.isNotBlank() },
                )
            }.getOrNull()

        private fun parseParticipantIds(raw: String): List<String> {
            val trimmed = raw.trim()
            return if (trimmed.startsWith("[")) {
                runCatching {
                    val jsonArray = JSONArray(trimmed)
                    List(jsonArray.length()) { index ->
                        jsonArray.optString(index)
                    }
                }.getOrElse { emptyList() }
            } else {
                trimmed.split(',').map(String::trim)
            }.filter { it.isNotBlank() }
        }

        private fun ConversationEntity.toDomain(): Conversation {
            val currentUserId = sessionManager.getUserId().orEmpty()
            val participants =
                parseParticipantIds(participantIds).map { participantId ->
                    val isCurrentUser = participantId == currentUserId
                    User(
                        id = participantId,
                        phoneNumber = "",
                        displayName =
                            when {
                                isCurrentUser -> "You"
                                type.equals("DIRECT", ignoreCase = true) && !title.isNullOrBlank() -> title.orEmpty()
                                else -> participantId
                            },
                        avatarUrl = if (isCurrentUser) null else avatarUrl,
                        createdAt = Instant.fromEpochMilliseconds(createdAt),
                    )
                }

            val derivedTitle =
                title ?: participants
                    .firstOrNull { it.id != currentUserId }
                    ?.displayName

            return Conversation(
                id = id,
                type =
                    try {
                        ConversationType.valueOf(type)
                    } catch (_: Exception) {
                        ConversationType.DIRECT
                    },
                participants = participants,
                title = derivedTitle,
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

        private fun ProtoConversation.toEntity(): ConversationEntity =
            ConversationEntity(
                id = id,
                type = type.uppercase(),
                title =
                    title
                        .ifEmpty {
                            participants.firstOrNull { it.displayName.isNotBlank() }?.displayName.orEmpty()
                        }.ifEmpty { null },
                avatarUrl =
                    avatarUrl
                        .ifEmpty {
                            participants.firstOrNull { it.avatarUrl.isNotBlank() }?.avatarUrl.orEmpty()
                        }.ifEmpty { null },
                participantIds =
                    JSONArray(
                        (participantIds.ifEmpty { participants.map { it.userId } }).toTypedArray(),
                    ).toString(),
                lastMessagePreview = lastMessagePreview.ifEmpty { null },
                lastMessageTimestamp = lastMessageTimestamp.takeIf { it > 0 },
                unreadCount = unreadCount,
                isPinned = isPinned,
                isMuted = isMuted,
                updatedAt = updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                createdAt = createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            )
    }
