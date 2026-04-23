package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.core.common.runCatchingResult
import com.sanchr.core.model.Message
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Sends an encrypted message to a conversation.
 * Handles encryption, optimistic UI update, and server delivery.
 */
class SendMessageUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val dispatcherProvider: DispatcherProvider,
    ) {
        /**
         * @param conversationId Target conversation.
         * @param content Plaintext message body (will be encrypted by repository).
         * @return [Result.Success] with the sent message, or [Result.Error] on failure.
         */
        suspend operator fun invoke(
            conversationId: String,
            content: String,
        ): Result<Message> =
            withContext(dispatcherProvider.io) {
                runCatchingResult {
                    require(content.isNotBlank()) { "Message content must not be blank" }
                    messageRepository.sendMessage(conversationId, content)
                }
            }
    }
