package com.sanchr.domain.messaging

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.crypto.sealed.SealedSenderCipher
import dagger.Lazy
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.libsignal.metadata.ProtocolDuplicateMessageException
import org.signal.libsignal.metadata.ProtocolInvalidMessageException
import org.signal.libsignal.metadata.ProtocolInvalidVersionException
import org.signal.libsignal.metadata.ProtocolLegacyMessageException
import org.signal.libsignal.metadata.ProtocolNoSessionException
import org.signal.libsignal.metadata.ProtocolUntrustedIdentityException
import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.InvalidVersionException
import org.signal.libsignal.protocol.LegacyMessageException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.UntrustedIdentityException

/**
 * How an inbound envelope reached the device. Derived from the RPC channel
 * (the M3 plan locks this as the sole discriminator — there is no wire-level
 * `is_sealed` flag).
 */
enum class EnvelopeKind {
    SEALED,
    NON_SEALED,
}

/**
 * Server-provided sender hint for the non-sealed path. Sealed envelopes
 * carry the sender inside the encrypted blob, so this field is `null`
 * for [EnvelopeKind.SEALED]. Deliberately kept a plain data class —
 * this type is shared between sync + domain layers and must not depend
 * on proto/infrastructure types.
 */
data class ServerProvidedSender(
    val senderUserId: String,
    val senderDeviceId: Int,
)

/**
 * Server-envelope metadata required to persist a decrypted message row.
 *
 * The inbound wire protocol (`sanchr.messaging.EncryptedEnvelope` and
 * `SealedInboundMessage`) carries `conversationId`, `messageId`, and
 * `contentType` alongside the ciphertext — there is no app-level inner
 * plaintext proto wrapping these fields, so the use case takes them as
 * context instead of parsing them out after decrypt.
 *
 * Passing `null` preserves the pre-Phase-C "decrypt only" behavior used by
 * tests that exercise the crypto chokepoint in isolation.
 */
data class IncomingEnvelopeContext(
    val conversationId: String,
    val messageId: String,
    val contentType: String,
)

/**
 * Single chokepoint for every inbound envelope on the receive path.
 *
 * Decrypts the envelope via the correct libsignal primitive, classifies
 * failures against the [FailureClass] taxonomy, persists non-transient
 * failures via [QuarantineEnvelopeUseCase], and best-effort schedules a
 * session rebuild when libsignal reports a missing session.
 *
 * Callers (FCM drain worker, RealtimeManager, SyncWorker) should not
 * catch libsignal exceptions themselves — everything funnels through
 * [receive] and yields a closed [EnvelopeDecryptResult].
 *
 * `Success` carries `plaintext` + sender metadata; callers are
 * responsible for parsing the plaintext protobuf and persisting the
 * MessageEntity. Phase-B intentionally keeps envelope-payload parsing
 * out of the use case — Phase D will own the payload contract.
 */
@Singleton
class ReceiveMessageUseCase
    @Inject
    constructor(
        private val sealedSenderCipher: SealedSenderCipher,
        private val signalSessionManager: SignalSessionManager,
        private val quarantineUseCase: QuarantineEnvelopeUseCase,
        private val messageRepository: Lazy<MessageRepository>,
        private val dispatchers: DispatcherProvider,
    ) {
        /**
         * Scope for fire-and-forget session rebuilds. A SupervisorJob is used
         * so that a failed rebuild for peer A does not cancel a concurrent
         * rebuild for peer B. Tests inject an equivalent scope via the
         * overridden constructor below.
         */
        private val sessionRecoveryScope: CoroutineScope =
            CoroutineScope(SupervisorJob() + dispatchers.io)

        /**
         * @param flushAckImmediately If true (default), successful decrypts
         *        synchronously flush the pending-ack batch to the server —
         *        right for single-shot callers (`RealtimeManager`). Batched
         *        drain paths (`MessageDrainWorker`) should pass `false` and
         *        call [MessageRepository.flushPendingAcks] once after the
         *        loop so the ack RPC count is O(1), not O(N).
         */
        suspend fun receive(
            envelopeBytes: ByteArray,
            kind: EnvelopeKind,
            serverTimestamp: Long,
            declaredSender: ServerProvidedSender?,
            envelopeContext: IncomingEnvelopeContext? = null,
            flushAckImmediately: Boolean = true,
        ): EnvelopeDecryptResult =
            withContext(dispatchers.io) {
                try {
                    val success = decryptByKind(envelopeBytes, kind, serverTimestamp, declaredSender)
                    envelopeContext?.let { ctx -> routeAndPersist(success, ctx, flushAckImmediately) }
                    success
                } catch (e: DuplicateMessageException) {
                    Log.d(TAG, "duplicate message (unsealed) ignored", e)
                    EnvelopeDecryptResult.DuplicateMessage
                } catch (e: ProtocolDuplicateMessageException) {
                    Log.d(TAG, "duplicate message (sealed) ignored", e)
                    EnvelopeDecryptResult.DuplicateMessage
                } catch (e: NoSessionException) {
                    scheduleSessionRebuild(declaredSender)
                    EnvelopeDecryptResult.SessionMissing
                } catch (e: ProtocolNoSessionException) {
                    scheduleSessionRebuildFromProtocol(e)
                    EnvelopeDecryptResult.SessionMissing
                } catch (e: InvalidMessageException) {
                    // Heuristic: libsignal raises InvalidMessageException both for
                    // tampered ciphertext and for "session out of sync" — the
                    // message-text is the only disambiguator exposed on the JVM.
                    if (e.message?.contains("session", ignoreCase = true) == true) {
                        scheduleSessionRebuild(declaredSender)
                        EnvelopeDecryptResult.SessionMissing
                    } else {
                        quarantineAndResult(envelopeBytes, serverTimestamp, declaredSender, FailureClass.INVALID_MESSAGE, e)
                    }
                } catch (e: ProtocolInvalidMessageException) {
                    quarantineAndResult(envelopeBytes, serverTimestamp, declaredSender, FailureClass.INVALID_MESSAGE, e)
                } catch (e: InvalidVersionException) {
                    quarantineAndResult(envelopeBytes, serverTimestamp, declaredSender, FailureClass.INVALID_MESSAGE, e)
                } catch (e: ProtocolInvalidVersionException) {
                    quarantineAndResult(envelopeBytes, serverTimestamp, declaredSender, FailureClass.INVALID_MESSAGE, e)
                } catch (e: LegacyMessageException) {
                    quarantineAndResult(envelopeBytes, serverTimestamp, declaredSender, FailureClass.INVALID_MESSAGE, e)
                } catch (e: ProtocolLegacyMessageException) {
                    quarantineAndResult(envelopeBytes, serverTimestamp, declaredSender, FailureClass.INVALID_MESSAGE, e)
                } catch (e: UntrustedIdentityException) {
                    // Peer's identity key rotated (non-sealed path). A
                    // catch-all CRYPTO_OTHER would let the UI conflate this
                    // with a transient crypto failure; quarantine
                    // distinctly so ops tooling / future safety-number UI
                    // can surface it as a trust decision rather than a
                    // retryable error.
                    quarantineAndResult(envelopeBytes, serverTimestamp, declaredSender, FailureClass.UNTRUSTED_IDENTITY, e)
                } catch (e: ProtocolUntrustedIdentityException) {
                    // Same treatment on the sealed-sender path.
                    quarantineAndResult(envelopeBytes, serverTimestamp, declaredSender, FailureClass.UNTRUSTED_IDENTITY, e)
                } catch (e: Exception) {
                    quarantineAndResult(envelopeBytes, serverTimestamp, declaredSender, FailureClass.CRYPTO_OTHER, e)
                }
            }

        private suspend fun decryptByKind(
            envelopeBytes: ByteArray,
            kind: EnvelopeKind,
            serverTimestamp: Long,
            declaredSender: ServerProvidedSender?,
        ): EnvelopeDecryptResult.Success =
            when (kind) {
                EnvelopeKind.SEALED -> {
                    val decrypted = sealedSenderCipher.sealedDecrypt(envelopeBytes, serverTimestamp)
                    EnvelopeDecryptResult.Success(
                        senderUserId = decrypted.senderUserId,
                        senderDeviceId = decrypted.senderDeviceId,
                        plaintext = decrypted.plaintext,
                        serverTimestamp = serverTimestamp,
                    )
                }
                EnvelopeKind.NON_SEALED -> {
                    val sender =
                        requireNotNull(declaredSender) {
                            "non-sealed envelopes require a server-provided sender hint"
                        }
                    val plaintext =
                        signalSessionManager.decrypt(
                            ciphertext = envelopeBytes,
                            senderId = sender.senderUserId,
                            senderDevice = sender.senderDeviceId,
                        )
                    EnvelopeDecryptResult.Success(
                        senderUserId = sender.senderUserId,
                        senderDeviceId = sender.senderDeviceId,
                        plaintext = plaintext,
                        serverTimestamp = serverTimestamp,
                    )
                }
            }

        /**
         * Routes a successfully decrypted plaintext via [SealedEnvelopeRouter]
         * and acts on the result — persisting a [RoutedPayload.UserMessage],
         * or just acking a [RoutedPayload.Control] without persisting it.
         *
         * The envelope is acked separately from persistence
         * ([MessageRepository.ackEnvelope], keyed by [ctx]'s own ids) rather
         * than relying on [MessageRepository.insertDecryptedMessage]'s
         * built-in ack staging, because that staging is keyed by whatever
         * conversation/message id the row itself uses — and a
         * [RoutedPayload.UserMessage] row is keyed by the *payload's* id
         * when one is present (see below), not the envelope's. The server's
         * delivery queue only recognizes the envelope's own id, so acking
         * must always use [ctx], independent of what got persisted.
         */
        private suspend fun routeAndPersist(
            success: EnvelopeDecryptResult.Success,
            ctx: IncomingEnvelopeContext,
            flushAckImmediately: Boolean,
        ) {
            when (val routed = SealedEnvelopeRouter.route(success.plaintext, fallbackContentType = ctx.contentType)) {
                is RoutedPayload.Control -> {
                    Log.d(TAG, "control payload (${routed.contentType}) received; not persisted as a message")
                    messageRepository.get().ackEnvelope(ctx.conversationId, ctx.messageId, flushAckImmediately)
                }
                is RoutedPayload.UserMessage -> {
                    // The row is keyed on the payload's own ids so that a
                    // resend of the same logical message (e.g. after a local
                    // DB write failed post-send) replaces the same row
                    // instead of duplicating it — see InnerPayload.messageId.
                    // A blank/absent payload id falls back to the envelope's.
                    messageRepository.get().insertDecryptedMessage(
                        conversationId = resolveId("conversation_id", routed.conversationId, ctx.conversationId),
                        messageId = resolveId("message_id", routed.messageId, ctx.messageId),
                        senderId = success.senderUserId,
                        content = routed.content,
                        contentType = routed.contentType,
                        timestamp = success.serverTimestamp,
                        // The real ack is staged below, keyed by the envelope's
                        // own ids — not by whatever this call just used.
                        flushAckImmediately = false,
                    )
                    messageRepository.get().ackEnvelope(ctx.conversationId, ctx.messageId, flushAckImmediately)
                }
                RoutedPayload.Ignored -> {
                    // No rule produces this today (see RoutedPayload.Ignored);
                    // logged rather than silently swallowed so a future
                    // regression here is visible.
                    Log.d(TAG, "payload routed to Ignored; not persisted, not acked")
                }
            }
        }

        /** Prefers [payloadValue] when non-blank; falls back to [envelopeValue] and logs at debug otherwise. */
        private fun resolveId(
            field: String,
            payloadValue: String?,
            envelopeValue: String,
        ): String =
            payloadValue?.takeIf { it.isNotBlank() } ?: envelopeValue.also {
                Log.d(TAG, "payload's $field is blank; falling back to the envelope's '$it'")
            }

        private suspend fun quarantineAndResult(
            envelopeBytes: ByteArray,
            serverTimestamp: Long,
            declaredSender: ServerProvidedSender?,
            failureClass: FailureClass,
            error: Throwable,
        ): EnvelopeDecryptResult.Quarantined {
            val reason = error.message ?: error::class.java.simpleName
            quarantineUseCase.quarantine(
                envelopeId = deriveEnvelopeId(envelopeBytes),
                payload = envelopeBytes,
                receivedAt = serverTimestamp,
                senderUserId = declaredSender?.senderUserId,
                senderDeviceId = declaredSender?.senderDeviceId,
                failureClass = failureClass,
                failureMessage = reason,
            )
            return EnvelopeDecryptResult.Quarantined(reason = reason, failureClass = failureClass)
        }

        private fun scheduleSessionRebuild(sender: ServerProvidedSender?) {
            val target = sender ?: return
            sessionRecoveryScope.launch {
                try {
                    signalSessionManager.establishSession(target.senderUserId, target.senderDeviceId)
                } catch (e: Exception) {
                    Log.w(TAG, "session rebuild for ${target.senderUserId}:${target.senderDeviceId} failed", e)
                }
            }
        }

        private fun scheduleSessionRebuildFromProtocol(e: ProtocolNoSessionException) {
            val senderUuid =
                runCatching { e.sender }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
            val deviceId = runCatching { e.senderDevice }.getOrNull() ?: return
            sessionRecoveryScope.launch {
                try {
                    signalSessionManager.establishSession(senderUuid, deviceId)
                } catch (t: Exception) {
                    Log.w(TAG, "session rebuild (sealed) for $senderUuid:$deviceId failed", t)
                }
            }
        }

        private fun deriveEnvelopeId(envelopeBytes: ByteArray): String {
            // Deterministic ID from the envelope bytes — lets duplicate-quarantine
            // inserts idempotently collapse onto the same row.
            val hash = envelopeBytes.contentHashCode().toLong() and 0xFFFFFFFFL
            return "env-" + UUID.nameUUIDFromBytes(envelopeBytes).toString() + "-" + hash
        }

        private companion object {
            private const val TAG = "ReceiveMessage"

            @Suppress("unused")
            private val FallbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
    }
