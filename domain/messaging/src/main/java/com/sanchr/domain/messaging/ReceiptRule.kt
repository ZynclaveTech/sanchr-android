package com.sanchr.domain.messaging

import com.sanchr.core.model.MessageStatus

/**
 * What a receipt is allowed to do to a message.
 *
 * A receipt names a message and a status, and both used to be written as
 * given: any status, onto any message, from anyone. That let a receipt mark
 * someone else's message, pull a read message back to delivered, or set a
 * sent message to `QUEUED` — which is the outbox's signal to send it again.
 *
 * A receipt says one thing: a message this user sent has been delivered, or
 * has been read. So it may only move one of this user's own messages forward
 * to one of those two.
 */
object ReceiptRule {
    /**
     * The statuses a message may be in for a receipt of [status] to apply,
     * or null if [status] is not something a receipt can report.
     */
    fun advancesFrom(status: MessageStatus): List<MessageStatus>? =
        when (status) {
            MessageStatus.DELIVERED -> NOT_YET_DELIVERED
            MessageStatus.READ -> NOT_YET_DELIVERED + MessageStatus.DELIVERED
            else -> null
        }

    /**
     * `FAILED` and `QUEUED` included: the send can have reached the other
     * phone while this one never heard so, and their receipt is then the
     * first news of it.
     */
    private val NOT_YET_DELIVERED =
        listOf(MessageStatus.QUEUED, MessageStatus.SENDING, MessageStatus.SENT, MessageStatus.FAILED)
}
