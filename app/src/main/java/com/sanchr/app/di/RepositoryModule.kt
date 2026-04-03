package com.sanchr.app.di

import com.sanchr.core.model.Conversation
import com.sanchr.core.model.Message
import com.sanchr.domain.messaging.MessageRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Singleton

/**
 * Provides repository implementations. These are stub implementations
 * until the data layer module is built out with Room-backed persistence.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideMessageRepository(): MessageRepository = StubMessageRepository()
}

/**
 * Stub implementation of [MessageRepository] for compilation purposes.
 * Will be replaced by a Room-backed implementation in core:data.
 */
private class StubMessageRepository : MessageRepository {

    override fun observeConversations(): Flow<List<Conversation>> = flowOf(emptyList())

    override fun observeMessages(conversationId: String): Flow<List<Message>> = flowOf(emptyList())

    override suspend fun sendMessage(conversationId: String, content: String): Message {
        throw NotImplementedError("MessageRepository not yet implemented")
    }

    override suspend fun markAsRead(conversationId: String) {
        // no-op stub
    }

    override suspend fun deleteMessage(messageId: String, forEveryone: Boolean) {
        // no-op stub
    }

    override suspend fun loadMoreMessages(
        conversationId: String,
        beforeTimestamp: Long,
        limit: Int,
    ): List<Message> = emptyList()

    override suspend fun createConversation(participantId: String): Conversation {
        throw NotImplementedError("MessageRepository not yet implemented")
    }

    override suspend fun setPinned(conversationId: String, pinned: Boolean) {
        // no-op stub
    }

    override suspend fun setArchived(conversationId: String, archived: Boolean) {
        // no-op stub
    }

    override suspend fun insertDecryptedMessage(
        conversationId: String,
        messageId: String,
        senderId: String,
        content: String,
        contentType: String,
        timestamp: Long,
    ) {
        // no-op stub
    }
}
