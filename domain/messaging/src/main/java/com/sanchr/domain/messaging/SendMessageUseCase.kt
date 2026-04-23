package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.core.common.runCatchingResult
import com.sanchr.core.crypto.EncryptFanOutEmptyException
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageStatus
import com.sanchr.proto.messaging.DeviceMessage
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.SendMessageRequest
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * Owns the outbound-message state machine.
 *
 * ```
 *   QUEUED ──(attemptSend)──▶ SENDING ──(RPC ok)──▶ SENT
 *      ▲                          │
 *      │                          └(RPC error, attempts < MAX)──▶ QUEUED
 *      │                          │
 *      │                          └(RPC error, attempts == MAX)──▶ FAILED
 *   (SendRetryWorker re-picks from QUEUED after backoff)
 * ```
 *
 * Persistence is driven exclusively via [MessageRepository]; encryption
 * fan-out and the network RPC are orchestrated here so the repository
 * stays a thin adapter over Room + the proto client. Callers:
 *
 *  - [invoke] — first attempt from the UI. Inserts a QUEUED row, then
 *    runs one attempt. Returns the resulting [Message] on success, or
 *    propagates the RPC failure on error (the row is left in QUEUED or
 *    FAILED depending on attempts — the caller can choose whether to
 *    surface that to the user).
 *  - [attemptSend] — used by `SendRetryWorker` to retry an existing
 *    QUEUED row without re-inserting.
 *
 * **Deviation from the plan, noted for the D3/envelope-kind follow-up:**
 * outbound still uses the legacy `SendMessage` RPC (non-sealed). The
 * switch to `SendSealedMessage` requires plumbing delivery-token fetch
 * (`GetDeliveryTokens`), which is a distinct concern outside D5's scope.
 * The envelope bytes handed to the server today already come from
 * `SignalSessionManager.encryptForAllDevices` — which prefers sealed
 * encryption when a sender certificate is cached — so on-wire the
 * ciphertext is already sealed in steady state.
 */
class SendMessageUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val sessionManager: SessionManager,
        private val signalSessionManager: SignalSessionManager,
        private val messagingClient: MessagingServiceClient,
        private val dispatcherProvider: DispatcherProvider,
    ) {
        /**
         * First-attempt send from the UI. Inserts a QUEUED row, runs one
         * attempt, and returns the resulting [Message] (in SENT on success,
         * QUEUED or FAILED on error — inspect the returned [Result]).
         */
        suspend operator fun invoke(
            conversationId: String,
            content: String,
            contentType: String = "text",
        ): Result<Message> =
            withContext(dispatcherProvider.io) {
                runCatchingResult {
                    require(content.isNotBlank()) { "Message content must not be blank" }
                    val entity =
                        messageRepository.enqueueOutboundMessage(
                            conversationId = conversationId,
                            content = content,
                            contentType = contentType,
                        )
                    attemptSendOrThrow(entity)
                }
            }

        /**
         * Retries a previously-QUEUED row. Called by `SendRetryWorker` on
         * the periodic backoff tick. Mirrors [invoke] but skips the
         * enqueue step.
         */
        suspend fun attemptSend(entity: MessageEntity): Result<Message> =
            withContext(dispatcherProvider.io) {
                runCatchingResult { attemptSendOrThrow(entity) }
            }

        private suspend fun attemptSendOrThrow(entity: MessageEntity): Message {
            val attempts =
                messageRepository.recordSendAttempt(
                    messageId = entity.id,
                    newStatus = MessageStatus.SENDING.name,
                )

            val selfUserId = sessionManager.getUserId().orEmpty()
            require(selfUserId.isNotBlank()) { "Missing current user id" }
            val recipients =
                messageRepository.getOutboundRecipients(
                    conversationId = entity.conversationId,
                    selfUserId = selfUserId,
                )

            try {
                val plaintext = entity.contentBody.toByteArray(Charsets.UTF_8)
                val deviceMessages =
                    recipients.flatMap { recipientId ->
                        signalSessionManager
                            .encryptForAllDevices(plaintext, recipientId)
                            .map { encrypted ->
                                DeviceMessage(
                                    recipientId = recipientId,
                                    deviceId = encrypted.deviceId,
                                    cipherText = encrypted.ciphertext,
                                )
                            }
                    }
                val response =
                    messagingClient.sendMessage(
                        SendMessageRequest(
                            conversationId = entity.conversationId,
                            deviceMessages = deviceMessages,
                            contentType = entity.contentType,
                        ),
                    )
                messageRepository.adoptServerId(
                    oldMessageId = entity.id,
                    newMessageId = response.messageId,
                    serverTimestamp = response.serverTimestamp,
                )
                return Message(
                    id = response.messageId,
                    conversationId = entity.conversationId,
                    senderId = entity.senderId,
                    content =
                        com.sanchr.core.model.MessageContent
                            .Text(entity.contentBody),
                    status = MessageStatus.SENT,
                    timestamp = Instant.fromEpochMilliseconds(response.serverTimestamp),
                )
            } catch (error: EncryptFanOutEmptyException) {
                // Transient: no ciphertext was produced for any recipient device
                // (typically a sender-certificate fetch failure). NEVER mark
                // FAILED on this branch — the row must remain eligible for
                // SendRetryWorker indefinitely, otherwise the message is lost.
                // The `attempts` counter has already been incremented by
                // recordSendAttempt; we deliberately skip the MAX_ATTEMPTS
                // gate here because a cert outage is not the user's fault and
                // is expected to clear on its own.
                messageRepository.requeueAfterFailure(entity.id)
                throw error
            } catch (error: Exception) {
                if (attempts >= MAX_ATTEMPTS) {
                    messageRepository.markSendFailed(entity.id)
                } else {
                    messageRepository.requeueAfterFailure(entity.id)
                }
                throw error
            }
        }

        private companion object {
            /**
             * Retry cap from spec §3. On the third failed attempt the row
             * transitions to FAILED and is no longer picked up by
             * `SendRetryWorker`; the user must manually retry or delete it.
             */
            const val MAX_ATTEMPTS = 3
        }
    }
