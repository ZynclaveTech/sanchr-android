package com.sanchr.domain.messaging

import com.sanchr.core.common.Result
import com.sanchr.core.common.asResult
import com.sanchr.core.model.Conversation
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observes the list of conversations as a reactive stream wrapped in [Result].
 */
class ObserveConversationsUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
) {
    operator fun invoke(): Flow<Result<List<Conversation>>> {
        return messageRepository.observeConversations().asResult()
    }
}
