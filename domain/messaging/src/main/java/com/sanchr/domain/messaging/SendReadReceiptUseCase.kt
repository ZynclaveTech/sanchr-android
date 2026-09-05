package com.sanchr.domain.messaging

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.profile.ProfileKeyStore
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.SendSealedMessageRequest
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import sanchr.messaging.Messaging

/**
 * Wall-clock source for `ReceiptUpdate.timestamp`. A thin, separately
 * injectable wrapper around [Clock.System] — not called inline — so
 * `SendReadReceiptUseCaseTest` can pin a fixed instant instead of asserting
 * against a moving `System.currentTimeMillis()`.
 */
class ReceiptClock
    @Inject
    constructor() {
        fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
    }

/**
 * The 0–3 s uniform-random jitter [SendReadReceiptUseCase] waits before
 * shipping a receipt, so the server cannot correlate read-time with
 * identity (spec: `receiptDelayNanoseconds`, iOS `MessageRepository.swift`
 * — the code samples `Double.random(in: 0...3)` seconds; a stale comment
 * there says "0-30 s", the code wins). A separate injectable seam, not a
 * bare `delay(Random...)` call inline, so the jitter is *random per
 * receipt* in production but deterministic in tests.
 */
class ReceiptJitter
    @Inject
    constructor() {
        suspend fun await() = delay(Random.nextLong(UPPER_BOUND_EXCLUSIVE_MS))

        private companion object {
            /** [0, 3000] ms inclusive — [Random.nextLong]'s upper bound is exclusive. */
            const val UPPER_BOUND_EXCLUSIVE_MS = 3_001L
        }
    }

/**
 * Sends a read receipt as a silent, sealed control envelope — the server
 * cannot read it, and (per [SealedSendKind.Control]) it never wakes an
 * offline device with a notification.
 *
 * Reuses [SendMessageUseCase.encryptAndWrapDeviceMessages] for the actual
 * encrypt-and-wrap loop rather than duplicating it; unlike an ordinary
 * message, a receipt has no [com.sanchr.core.database.entity.MessageEntity]
 * row and no retry state machine, so it drives [DeliveryTokenStore] and
 * [MessagingServiceClient] directly instead of going through
 * [SendMessageUseCase.attemptSend].
 *
 * Five behavioural rules, all load-bearing:
 * 1. [UserPreferences.readReceiptsEnabled] gates everything — false means
 *    no jitter, no token, no send.
 * 2. 1:1 only — [MessageRepository.oneToOneRecipient] resolves the single
 *    other participant of a DIRECT conversation, returning null (silently
 *    dropped) for a GROUP, a missing conversation, or anything else that
 *    is not exactly one other participant.
 * 3. The jitter ([ReceiptJitter]) is random per receipt.
 * 4. The preference and the peer are resolved *before* [DeliveryTokenStore.acquire]
 *    — tokens are single-use and rate-limited, so nothing is spent on a
 *    receipt that will not be sent.
 * 5. Failure is silent: logged and dropped, never thrown, never retried,
 *    and never touches local read state (already committed by the time
 *    this runs) — except [CancellationException], which propagates
 *    unchanged, matching [SendMessageUseCase]'s `adoptServerIdSafely`.
 *
 * No [MAX_DEVICE_MESSAGES][SendMessageUseCase] cap is enforced here: that
 * limit exists for a message's fan-out across every participant of a
 * (potentially large) group, but a receipt fans out to exactly one peer's
 * linked devices, and Android is primary-device only today — the peer
 * would need on the order of a hundred linked devices to approach it. If
 * that ever changes, an oversized send still fails safely: rule 5 logs and
 * drops it exactly as any other send failure.
 */
class SendReadReceiptUseCase
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val sessionManager: SessionManager,
        private val userPreferences: UserPreferences,
        private val sendMessageUseCase: SendMessageUseCase,
        private val deliveryTokenStore: DeliveryTokenStore,
        private val messagingClient: MessagingServiceClient,
        private val dispatcherProvider: DispatcherProvider,
        private val receiptClock: ReceiptClock,
        private val receiptJitter: ReceiptJitter,
        private val profileKeyStore: ProfileKeyStore,
    ) {
        suspend operator fun invoke(
            conversationId: String,
            messageId: String,
        ) {
            withContext(dispatcherProvider.io) {
                try {
                    sendReceipt(conversationId, messageId)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w(TAG, "read receipt send failed for conversation=$conversationId message=$messageId", error)
                }
            }
        }

        private suspend fun sendReceipt(
            conversationId: String,
            messageId: String,
        ) {
            if (!userPreferences.readReceiptsEnabled.first()) return

            val selfUserId = sessionManager.getUserId()
            if (selfUserId.isNullOrBlank()) return

            val peerId = messageRepository.oneToOneRecipient(conversationId, selfUserId) ?: return

            receiptJitter.await()

            val receipt =
                Messaging.ReceiptUpdate
                    .newBuilder()
                    .setConversationId(conversationId)
                    .setMessageId(messageId)
                    .setRecipientId(selfUserId)
                    .setStatus(STATUS_READ)
                    .setTimestamp(receiptClock.nowEpochMillis())
                    .build()

            val payload =
                InnerPayload(
                    conversationId = "",
                    messageId = null,
                    contentType = CONTENT_TYPE_RECEIPT,
                    content = receipt.toByteArray(),
                    // Receipts are the most frequent sealed traffic, so they are
                    // the most reliable carrier for our Profile Key (see
                    // SendMessageUseCase).
                    senderProfileKey = profileKeyStore.ownProfileKey(),
                )

            val deviceMessages =
                sendMessageUseCase.encryptAndWrapDeviceMessages(
                    plaintext = payload.encode(),
                    recipients = listOf(peerId),
                    conversationId = "",
                    kind = SealedSendKind.Control,
                )
            // Nothing to send — e.g. the peer has no key-capable devices.
            // No token has been spent yet (rule 4).
            if (deviceMessages.isEmpty()) return

            val token = deliveryTokenStore.acquire()
            messagingClient.sendSealedMessage(
                SendSealedMessageRequest(
                    deliveryToken = token,
                    deviceMessages = deviceMessages,
                ),
            )
        }

        private companion object {
            private const val TAG = "SendReadReceiptUseCase"
            private const val CONTENT_TYPE_RECEIPT = "receipt/v1"
            private const val STATUS_READ = "read"
        }
    }
