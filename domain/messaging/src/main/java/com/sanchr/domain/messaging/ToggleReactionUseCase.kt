package com.sanchr.domain.messaging

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.Reaction
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Adds our reaction to a message, or removes it when we already reacted
 * with that emoji, the way iOS `toggleReaction` does: applied locally first,
 * sent with `SendReaction`, and reverted if the server refuses it.
 */
class ToggleReactionUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val messagingClient: MessagingServiceClient,
        private val sessionManager: SessionManager,
        private val dispatcherProvider: DispatcherProvider,
    ) {
        /** True when the reaction is now present, false when it is now absent. */
        suspend operator fun invoke(
            conversationId: String,
            messageId: String,
            emoji: String,
        ): Boolean =
            withContext(dispatcherProvider.io) {
                val self = sessionManager.getUserId().orEmpty()
                require(self.isNotBlank()) { "Missing current user id" }
                val removing = messageRepository.reactionsFor(messageId).any { it.userId == self && it.emoji == emoji }
                val now = System.currentTimeMillis()
                messageRepository.applyReaction(messageId, self, emoji, removed = removing, timestampMillis = now)
                try {
                    messagingClient.sendReaction(
                        Reaction(
                            messageId = messageId,
                            conversationId = conversationId,
                            userId = self,
                            emoji = emoji,
                            removed = removing,
                            timestamp = now,
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "SendReaction failed; reverting", e)
                    messageRepository.applyReaction(messageId, self, emoji, removed = !removing, timestampMillis = now)
                    throw e
                }
                !removing
            }

        private companion object {
            const val TAG = "ToggleReactionUseCase"
        }
    }
