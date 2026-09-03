package com.sanchr.domain.messaging

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.core.common.runCatchingResult
import com.sanchr.core.crypto.EncryptFanOutEmptyException
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageStatus
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.SealedDeviceMessage
import com.sanchr.proto.messaging.SendSealedMessageRequest
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.signal.libsignal.protocol.UntrustedIdentityException

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
 * Outbound is genuinely sealed: the plaintext handed to
 * [SignalSessionManager.encryptForAllDevices] is an [InnerPayload] (not the
 * bare body), the terminal RPC is `SendSealedMessage`, and its only
 * credential is a single-use [DeliveryTokenStore] token — no header on that
 * call identifies the sender. There is deliberately no fallback to the
 * legacy `SendMessage` RPC: a sealed failure keeps the existing retry /
 * failure-classification behaviour rather than silently downgrading to an
 * identified send.
 */
class SendMessageUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val sessionManager: SessionManager,
        private val signalSessionManager: SignalSessionManager,
        private val messagingClient: MessagingServiceClient,
        private val deliveryTokenStore: DeliveryTokenStore,
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

            val deviceMessages = encryptFanOut(entity, recipients, selfUserId)

            guardZeroRecipients(entity, recipients, deviceMessages)
            guardFanOutSize(entity, deviceMessages)

            try {
                val token = deliveryTokenStore.acquire()
                val response =
                    messagingClient.sendSealedMessage(
                        SendSealedMessageRequest(
                            deliveryToken = token,
                            deviceMessages = deviceMessages,
                        ),
                    )
                messageRepository.adoptServerId(
                    oldMessageId = entity.id,
                    // No server-assigned id exists on a sealed send — the
                    // sender assigns it (see InnerPayload.messageId) and the
                    // recipient reads it back out of the envelope. The local
                    // id is therefore already permanent; re-adopting it here
                    // (rather than skipping the call) still flips the row to
                    // SENT and stamps the server timestamp in one UPDATE.
                    newMessageId = entity.id,
                    serverTimestamp = response.serverTimestamp,
                )
                // Best-effort top-up: a failure here must not undo a send
                // that has already succeeded and been adopted above, so it
                // is not allowed to fall into the catch below and requeue
                // (or fail) an already-SENT row.
                runCatching { deliveryTokenStore.replenishIfNeeded() }
                    .onFailure { Log.w(TAG, "replenishIfNeeded failed after a successful send", it) }
                return Message(
                    id = entity.id,
                    conversationId = entity.conversationId,
                    senderId = entity.senderId,
                    content =
                        com.sanchr.core.model.MessageContent
                            .Text(entity.contentBody),
                    status = MessageStatus.SENT,
                    timestamp = Instant.fromEpochMilliseconds(response.serverTimestamp),
                )
            } catch (error: Exception) {
                if (attempts >= MAX_ATTEMPTS) {
                    messageRepository.markSendFailed(
                        messageId = entity.id,
                        failureReason = error.message ?: error::class.java.simpleName,
                        failureClass = FailureClass.CRYPTO_OTHER,
                    )
                } else {
                    messageRepository.requeueAfterFailure(entity.id)
                }
                throw error
            }
        }

        /**
         * Builds the [InnerPayload] once, encrypts it per recipient device,
         * and returns the combined [SealedDeviceMessage] list. Extracted
         * from [attemptSendOrThrow] so two non-retriable branches
         * ([EncryptFanOutEmptyException], [UntrustedIdentityException]) can
         * be handled distinctly without exceeding the sibling function's
         * throws budget.
         */
        private suspend fun encryptFanOut(
            entity: MessageEntity,
            recipients: List<String>,
            selfUserId: String,
        ): List<SealedDeviceMessage> =
            try {
                val payload =
                    InnerPayload(
                        conversationId = entity.conversationId,
                        messageId = entity.id,
                        contentType = entity.contentType,
                        content = entity.contentBody.toByteArray(Charsets.UTF_8),
                        senderUserId = selfUserId,
                        senderDeviceId = sessionManager.getDeviceId()?.toIntOrNull() ?: 0,
                    )
                val plaintext = payload.encode()
                recipients.flatMap { recipientId ->
                    signalSessionManager
                        .encryptForAllDevices(plaintext, recipientId)
                        .map { encrypted ->
                            SealedDeviceMessage(
                                recipientId = recipientId,
                                deviceId = encrypted.deviceId,
                                sealedEnvelope = encrypted.ciphertext,
                                conversationId = entity.conversationId,
                                silent = false,
                            )
                        }
                }
            } catch (error: EncryptFanOutEmptyException) {
                // Transient: no ciphertext was produced for any recipient
                // device (typically a sender-certificate fetch failure).
                // NEVER mark FAILED on this branch — the row must remain
                // eligible for SendRetryWorker indefinitely, otherwise
                // the message is lost. We deliberately skip the
                // MAX_ATTEMPTS gate here because a cert outage is not
                // the user's fault and is expected to clear on its own.
                messageRepository.requeueAfterFailure(entity.id)
                throw error
            } catch (error: org.signal.libsignal.metadata.ProtocolUntrustedIdentityException) {
                // Sealed-sender variant of the same condition — libsignal
                // emits this type from SealedSessionCipher paths rather than
                // the protocol-level [UntrustedIdentityException] caught below.
                messageRepository.markSendFailed(
                    messageId = entity.id,
                    failureReason = UNTRUSTED_IDENTITY_REASON,
                    failureClass = FailureClass.UNTRUSTED_IDENTITY,
                )
                throw error
            } catch (error: UntrustedIdentityException) {
                // Peer's identity key rotated. Retrying will fail the
                // same way until the user explicitly re-trusts the new
                // key via a safety-number screen (M6). Mark the row
                // terminally FAILED with a typed class so the UI renders
                // a distinct affordance (warning icon + "Peer's safety
                // number changed" tooltip) instead of the generic
                // retry-me error icon.
                messageRepository.markSendFailed(
                    messageId = entity.id,
                    failureReason = UNTRUSTED_IDENTITY_REASON,
                    failureClass = FailureClass.UNTRUSTED_IDENTITY,
                )
                throw error
            }

        /**
         * Zero-recipient guard. Never ship a `SendSealedMessage` RPC with an
         * empty device list — the server treats it as a successful no-op
         * send and the message is silently lost (the "silent SENT" bug
         * from the M4 review). Marks the row terminally FAILED with
         * [FailureClass.NO_RECIPIENTS] and throws
         * [NoRecipientsReachableException]; callers MUST NOT requeue.
         *
         * The `recipients.isEmpty()` check is defense-in-depth —
         * [MessageRepository.getOutboundRecipients] already `require`s
         * non-empty, but that contract may later be relaxed.
         */
        private suspend fun guardZeroRecipients(
            entity: MessageEntity,
            recipients: List<String>,
            deviceMessages: List<SealedDeviceMessage>,
        ) {
            if (recipients.isNotEmpty() && deviceMessages.isNotEmpty()) return
            messageRepository.markSendFailed(
                messageId = entity.id,
                failureReason = NO_RECIPIENTS_REASON,
                failureClass = FailureClass.NO_RECIPIENTS,
            )
            throw NoRecipientsReachableException(entity.conversationId)
        }

        /**
         * Fan-out size guard, checked *before* [DeliveryTokenStore.acquire]
         * is ever called. The server rejects a `SendSealedMessage` call
         * carrying more than [MAX_DEVICE_MESSAGES] device messages with
         * `INVALID_ARGUMENT` — but it consumes the delivery token *before*
         * it validates, so retrying an oversized fan-out would burn a fresh
         * token on every attempt. Marks the row terminally FAILED with
         * [FailureClass.TOO_MANY_RECIPIENTS] and throws
         * [TooManyDeviceMessagesException] rather than silently truncating
         * the recipient list; callers MUST NOT requeue.
         */
        private suspend fun guardFanOutSize(
            entity: MessageEntity,
            deviceMessages: List<SealedDeviceMessage>,
        ) {
            if (deviceMessages.size <= MAX_DEVICE_MESSAGES) return
            messageRepository.markSendFailed(
                messageId = entity.id,
                failureReason = TOO_MANY_RECIPIENTS_REASON,
                failureClass = FailureClass.TOO_MANY_RECIPIENTS,
            )
            throw TooManyDeviceMessagesException(entity.conversationId, deviceMessages.size)
        }

        private companion object {
            private const val TAG = "SendMessageUseCase"

            /**
             * Retry cap from spec §3. On the third failed attempt the row
             * transitions to FAILED and is no longer picked up by
             * `SendRetryWorker`; the user must manually retry or delete it.
             */
            const val MAX_ATTEMPTS = 3

            const val NO_RECIPIENTS_REASON = "No recipients reachable"
            const val UNTRUSTED_IDENTITY_REASON = "Peer's safety number changed"
            const val TOO_MANY_RECIPIENTS_REASON = "Recipient fan-out exceeds the 100 device-message limit"
        }
    }

/** Server limit from `sealed_handler.rs`: max device messages per `SendSealedMessage` call. */
private const val MAX_DEVICE_MESSAGES = 100

/**
 * Thrown by [SendMessageUseCase.attemptSend] when the fan-out produced no
 * `DeviceMessage` rows — either the conversation had no remote participants
 * or every recipient returned an empty key-capable device list. Signals a
 * *terminal* failure: the outbound row has already been marked FAILED with
 * [FailureClass.NO_RECIPIENTS] and MUST NOT be requeued.
 */
class NoRecipientsReachableException(
    val conversationId: String,
) : RuntimeException("No recipients reachable for conversation $conversationId")

/**
 * Thrown by [SendMessageUseCase.attemptSend] when the encryption fan-out
 * produced more than [MAX_DEVICE_MESSAGES] `SealedDeviceMessage` rows for
 * one send. Signals a *terminal* failure: the outbound row has already
 * been marked FAILED with [FailureClass.TOO_MANY_RECIPIENTS] and MUST NOT
 * be requeued. Thrown before [DeliveryTokenStore.acquire] is called, so no
 * token is spent on a send that can only ever be rejected.
 */
class TooManyDeviceMessagesException(
    val conversationId: String,
    val deviceMessageCount: Int,
) : RuntimeException(
        "Fan-out for conversation $conversationId produced $deviceMessageCount device messages, " +
            "exceeding the server's limit of $MAX_DEVICE_MESSAGES",
    )
