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
import kotlinx.coroutines.CancellationException
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

            // Everything up to and including this RPC is the send itself: a
            // failure here means the server never accepted the message, so
            // it is safe (and correct) to requeue or terminally fail below.
            val response =
                try {
                    val token = deliveryTokenStore.acquire()
                    messagingClient.sendSealedMessage(
                        SendSealedMessageRequest(
                            deliveryToken = token,
                            deviceMessages = deviceMessages,
                        ),
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

            // The RPC above succeeded: the server has accepted and delivered
            // the message. Everything from here on is post-send bookkeeping;
            // see adoptServerIdSafely and replenishTokenPoolSafely for why
            // an ordinary failure there must never be attributed back to the
            // send, and why CancellationException is handled differently.
            // Both are extracted to their own functions — like encryptFanOut
            // above — so this function's own throws budget stays untouched.
            adoptServerIdSafely(entity, response.serverTimestamp)
            replenishTokenPoolSafely()

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
        }

        /**
         * Flips the row to SENT and stamps the server timestamp, called
         * with `newMessageId == oldMessageId` because a sealed send has no
         * server-assigned id to adopt (see [attemptSendOrThrow]'s call
         * site). Extracted so its own `catch` clauses don't count against
         * [attemptSendOrThrow]'s throws budget.
         *
         * By the time this runs, the RPC has already succeeded — the
         * server has the message — so an ordinary failure here (disk I/O,
         * a full disk) is logged and swallowed rather than reported via
         * [MessageRepository.requeueAfterFailure] or
         * [MessageRepository.markSendFailed]: doing either would mark a
         * delivered message FAILED, or bounce it back to "sending" in the
         * UI. The row may be left QUEUED and get resent by
         * `SendRetryWorker` instead — safe, not silently lossy, because the
         * id handed to the server inside `InnerPayload.messageId` is this
         * same, stable local id on every retry, and the receive path's
         * `insertMessage` is REPLACE-on-conflict keyed on that id, so a
         * resend lands as an idempotent replace rather than a duplicate.
         *
         * [CancellationException] is the one exception NOT swallowed:
         * absorbing it here would let this function return normally after
         * its coroutine scope was already cancelled, a structured-
         * concurrency violation (see `SessionRefresher.refresh` for the
         * same rule applied to the same shape). A send cancelled at this
         * point will not report SENT and the row stays QUEUED — safe, for
         * the same id-stability reason given above.
         */
        private suspend fun adoptServerIdSafely(
            entity: MessageEntity,
            serverTimestamp: Long,
        ) {
            try {
                messageRepository.adoptServerId(
                    oldMessageId = entity.id,
                    newMessageId = entity.id,
                    serverTimestamp = serverTimestamp,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "adoptServerId failed after a successful sealed send for message ${entity.id}", error)
            }
        }

        /**
         * Best-effort token-pool top-up after a successful send. Extracted
         * for the same reason as [adoptServerIdSafely]: a failure here —
         * cancellation aside — must not undo a send that already succeeded,
         * and its own `catch` clauses must not count against
         * [attemptSendOrThrow]'s throws budget.
         */
        private suspend fun replenishTokenPoolSafely() {
            try {
                deliveryTokenStore.replenishIfNeeded()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "replenishIfNeeded failed after a successful send", error)
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
                val rawDeviceId = sessionManager.getDeviceId()
                val senderDeviceId = rawDeviceId?.toIntOrNull()
                if (senderDeviceId == null) {
                    // Should be unreachable post-sign-in — a registered device
                    // always has an id. Logged rather than made fatal so an
                    // unexpected null still degrades to InnerPayload.senderDeviceId
                    // = 0 instead of blocking the send outright.
                    Log.w(TAG, "sessionManager.getDeviceId() was '$rawDeviceId'; InnerPayload.senderDeviceId falling back to 0")
                }
                val payload =
                    InnerPayload(
                        conversationId = entity.conversationId,
                        messageId = entity.id,
                        contentType = entity.contentType,
                        content = entity.contentBody.toByteArray(Charsets.UTF_8),
                        senderUserId = selfUserId,
                        senderDeviceId = senderDeviceId ?: 0,
                    )
                val plaintext = payload.encode()
                encryptAndWrapDeviceMessages(
                    plaintext = plaintext,
                    recipients = recipients,
                    conversationId = entity.conversationId,
                    kind = SealedSendKind.Message,
                )
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
         * Encrypts [plaintext] for every device of every recipient and wraps
         * each ciphertext into a [SealedDeviceMessage] carrying [kind]'s
         * [SealedSendKind.isSilent] flag. This is the "encrypt-and-wrap"
         * half of [encryptFanOut] above, with the InnerPayload construction
         * and message-specific failure handling (requeue vs. mark-failed)
         * left behind in that function.
         *
         * `internal`, not `private`: a read receipt is a different sealed
         * send (a [SealedSendKind.Control] payload the caller builds itself,
         * with its own failure handling — it is not a message and must not
         * be requeued or marked FAILED the way one is), but it is still just
         * bytes encrypted per-recipient-device and wrapped the same way, so
         * it reuses this function directly rather than re-implementing the
         * loop over [SignalSessionManager.encryptForAllDevices].
         */
        internal suspend fun encryptAndWrapDeviceMessages(
            plaintext: ByteArray,
            recipients: List<String>,
            conversationId: String,
            kind: SealedSendKind,
        ): List<SealedDeviceMessage> =
            recipients.flatMap { recipientId ->
                signalSessionManager
                    .encryptForAllDevices(plaintext, recipientId)
                    .map { encrypted ->
                        SealedDeviceMessage(
                            recipientId = recipientId,
                            deviceId = encrypted.deviceId,
                            sealedEnvelope = encrypted.ciphertext,
                            conversationId = conversationId,
                            silent = kind.isSilent,
                        )
                    }
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
