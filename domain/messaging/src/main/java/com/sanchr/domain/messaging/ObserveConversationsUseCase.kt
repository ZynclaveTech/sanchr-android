package com.sanchr.domain.messaging

import com.sanchr.core.common.Result
import com.sanchr.core.common.asResult
import com.sanchr.core.model.Conversation
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Observes the list of conversations as a reactive stream wrapped in [Result].
 */
class ObserveConversationsUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
    ) {
        operator fun invoke(): Flow<Result<List<Conversation>>> = messageRepository.observeConversations().asResult()
    }
