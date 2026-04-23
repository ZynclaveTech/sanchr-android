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
import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.InvalidVersionException
import org.signal.libsignal.protocol.LegacyMessageException
import org.signal.libsignal.protocol.NoSessionException

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

        suspend fun receive(
            envelopeBytes: ByteArray,
            kind: EnvelopeKind,
            serverTimestamp: Long,
            declaredSender: ServerProvidedSender?,
            envelopeContext: IncomingEnvelopeContext? = null,
        ): EnvelopeDecryptResult =
            withContext(dispatchers.io) {
                try {
                    val success = decryptByKind(envelopeBytes, kind, serverTimestamp, declaredSender)
                    envelopeContext?.let { ctx ->
                        messageRepository.get().insertDecryptedMessage(
                            conversationId = ctx.conversationId,
                            messageId = ctx.messageId,
                            senderId = success.senderUserId,
                            content = String(success.plaintext, Charsets.UTF_8),
                            contentType = ctx.contentType,
                            timestamp = success.serverTimestamp,
                        )
                    }
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
