package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Sends a "delivered" receipt for a message that has just arrived, without
 * holding up the receive path that noticed it.
 *
 * A receipt is an encrypted send of its own — a token, a jittered wait, a
 * network call. Receiving a message must not wait on any of that, or fail
 * because of it.
 */
@Singleton
class DeliveryReceiptDispatcher
    @Inject
    constructor(
        // Lazy: sending depends on the same repository the receive path does.
        private val sendReceipt: Lazy<SendReadReceiptUseCase>,
        dispatchers: DispatcherProvider,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

        fun messageArrived(
            conversationId: String,
            messageId: String,
        ) {
            scope.launch { sendReceipt.get().delivered(conversationId, messageId) }
        }
    }
