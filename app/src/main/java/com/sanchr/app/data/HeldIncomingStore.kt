package com.sanchr.app.data

import com.sanchr.core.database.dao.HeldIncomingDao
import com.sanchr.core.database.entity.HeldIncomingEntity
import com.sanchr.domain.messaging.EnvelopeDecryptResult
import com.sanchr.domain.messaging.HeldIncoming
import com.sanchr.domain.messaging.IncomingEnvelopeContext
import javax.inject.Inject

/** Where a decrypted message that could not be filed is kept; see `HeldIncomingEntity`. */
class HeldIncomingStore
    @Inject
    constructor(
        private val dao: HeldIncomingDao,
    ) {
        suspend fun hold(held: HeldIncoming) {
            dao.hold(
                HeldIncomingEntity(
                    senderUserId = held.decrypted.senderUserId,
                    senderDeviceId = held.decrypted.senderDeviceId,
                    plaintext = held.decrypted.plaintext,
                    serverTimestamp = held.decrypted.serverTimestamp,
                    envelopeConversationId = held.envelope.conversationId,
                    envelopeMessageId = held.envelope.messageId,
                    contentType = held.envelope.contentType,
                    attempts = held.attempts,
                    heldAt = System.currentTimeMillis(),
                ),
            )
        }

        /** Oldest first. */
        suspend fun all(): List<HeldIncoming> =
            dao.all().map { row ->
                HeldIncoming(
                    id = row.id,
                    decrypted =
                        EnvelopeDecryptResult.Success(
                            senderUserId = row.senderUserId,
                            senderDeviceId = row.senderDeviceId,
                            plaintext = row.plaintext,
                            serverTimestamp = row.serverTimestamp,
                        ),
                    envelope = IncomingEnvelopeContext(row.envelopeConversationId, row.envelopeMessageId, row.contentType),
                    attempts = row.attempts,
                    heldAtMillis = row.heldAt,
                )
            }

        suspend fun release(id: Long) = dao.release(id)

        suspend fun noteAttempt(id: Long) = dao.noteAttempt(id)
    }
