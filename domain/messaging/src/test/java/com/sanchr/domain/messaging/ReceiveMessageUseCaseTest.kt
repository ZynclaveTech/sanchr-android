package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.crypto.sealed.SealedSenderCipher
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.UntrustedIdentityException

class ReceiveMessageUseCaseTest {
    private val sealedSenderCipher = mockk<SealedSenderCipher>()
    private val signalSessionManager = mockk<SignalSessionManager>()
    private val quarantineUseCase = mockk<QuarantineEnvelopeUseCase>()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val messageRepositoryLazy = Lazy<MessageRepository> { messageRepository }
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }

    private val useCase =
        ReceiveMessageUseCase(
            sealedSenderCipher,
            signalSessionManager,
            quarantineUseCase,
            messageRepositoryLazy,
            dispatchers,
        )

    private val bytes = byteArrayOf(10, 20, 30)
    private val senderHint = ServerProvidedSender("bob-uuid", 2)

    @Test
    fun sealed_success_returns_decrypted_plaintext_and_sender() =
        runTest {
            val plaintext = byteArrayOf(1, 2, 3)
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 42L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = plaintext,
                )

            val result = useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null)

            assertTrue(result is EnvelopeDecryptResult.Success)
            assertEquals("alice-uuid", result.senderUserId)
            assertEquals(1, result.senderDeviceId)
            assertTrue(plaintext.contentEquals(result.plaintext))
            assertEquals(42L, result.serverTimestamp)
        }

    @Test
    fun non_sealed_success_uses_declared_sender() =
        runTest {
            val plaintext = byteArrayOf(7, 7, 7)
            coEvery { signalSessionManager.decrypt(bytes, "bob-uuid", 2) } returns plaintext

            val result = useCase.receive(bytes, EnvelopeKind.NON_SEALED, 99L, senderHint)

            assertTrue(result is EnvelopeDecryptResult.Success)
            assertEquals("bob-uuid", result.senderUserId)
            assertEquals(2, result.senderDeviceId)
            assertTrue(plaintext.contentEquals(result.plaintext))
        }

    @Test
    fun duplicate_message_returns_duplicate_without_quarantine() =
        runTest {
            coEvery { signalSessionManager.decrypt(any(), any(), any()) } throws
                DuplicateMessageException("replay")

            val result = useCase.receive(bytes, EnvelopeKind.NON_SEALED, 1L, senderHint)

            assertEquals(EnvelopeDecryptResult.DuplicateMessage, result)
            coVerify(exactly = 0) { quarantineUseCase.quarantine(any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun no_session_returns_session_missing_and_schedules_rebuild() =
        runTest {
            coEvery { signalSessionManager.decrypt(any(), any(), any()) } throws
                NoSessionException("no session for address")
            coEvery { signalSessionManager.establishSession("bob-uuid", 2) } returns mockk(relaxed = true)

            val result = useCase.receive(bytes, EnvelopeKind.NON_SEALED, 1L, senderHint)

            assertEquals(EnvelopeDecryptResult.SessionMissing, result)
            coVerify { signalSessionManager.establishSession("bob-uuid", 2) }
        }

    @Test
    fun invalid_message_with_session_hint_returns_session_missing() =
        runTest {
            coEvery { signalSessionManager.decrypt(any(), any(), any()) } throws
                InvalidMessageException("Bad session state")
            coEvery { signalSessionManager.establishSession(any(), any()) } returns mockk(relaxed = true)

            val result = useCase.receive(bytes, EnvelopeKind.NON_SEALED, 1L, senderHint)

            assertEquals(EnvelopeDecryptResult.SessionMissing, result)
        }

    @Test
    fun invalid_message_without_session_hint_quarantines() =
        runTest {
            coEvery { signalSessionManager.decrypt(any(), any(), any()) } throws
                InvalidMessageException("bad MAC")
            coJustRun {
                quarantineUseCase.quarantine(any(), any(), any(), any(), any(), any(), any())
            }

            val result = useCase.receive(bytes, EnvelopeKind.NON_SEALED, 55L, senderHint)

            assertTrue(result is EnvelopeDecryptResult.Quarantined)
            assertEquals(FailureClass.INVALID_MESSAGE, result.failureClass)
            coVerify {
                quarantineUseCase.quarantine(
                    envelopeId = any(),
                    payload = bytes,
                    receivedAt = 55L,
                    senderUserId = "bob-uuid",
                    senderDeviceId = 2,
                    failureClass = FailureClass.INVALID_MESSAGE,
                    failureMessage = "bad MAC",
                )
            }
        }

    @Test
    fun receive_quarantines_envelope_with_UNTRUSTED_IDENTITY_when_peer_identity_changed() =
        runTest {
            coEvery { signalSessionManager.decrypt(any(), any(), any()) } throws
                UntrustedIdentityException("bob-uuid", mockk(relaxed = true))
            coJustRun {
                quarantineUseCase.quarantine(any(), any(), any(), any(), any(), any(), any())
            }

            val result = useCase.receive(bytes, EnvelopeKind.NON_SEALED, 77L, senderHint)

            assertTrue(result is EnvelopeDecryptResult.Quarantined)
            assertEquals(FailureClass.UNTRUSTED_IDENTITY, result.failureClass)
            coVerify {
                quarantineUseCase.quarantine(
                    envelopeId = any(),
                    payload = bytes,
                    receivedAt = 77L,
                    senderUserId = "bob-uuid",
                    senderDeviceId = 2,
                    failureClass = FailureClass.UNTRUSTED_IDENTITY,
                    failureMessage = any(),
                )
            }
        }

    @Test
    fun unknown_crypto_error_quarantines_as_crypto_other() =
        runTest {
            coEvery { signalSessionManager.decrypt(any(), any(), any()) } throws RuntimeException("boom")
            coJustRun {
                quarantineUseCase.quarantine(any(), any(), any(), any(), any(), any(), any())
            }

            val result = useCase.receive(bytes, EnvelopeKind.NON_SEALED, 1L, senderHint)

            assertTrue(result is EnvelopeDecryptResult.Quarantined)
            assertEquals(FailureClass.CRYPTO_OTHER, result.failureClass)
        }

    // ── Task 6: routing an inner payload ahead of persistence ──

    private val ctx = IncomingEnvelopeContext(conversationId = "env-conv", messageId = "env-msg", contentType = "text")

    @Test
    fun sealed_success_with_inner_payload_persists_using_the_payload_s_ids_and_content() =
        runTest {
            val payload =
                InnerPayload(
                    conversationId = "payload-conv",
                    messageId = "payload-msg",
                    contentType = "text",
                    content = "hi there".toByteArray(),
                )
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 42L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = payload.encode(),
                )

            val result = useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            assertTrue(result is EnvelopeDecryptResult.Success)
            coVerify {
                messageRepository.insertDecryptedMessage(
                    conversationId = "payload-conv",
                    messageId = "payload-msg",
                    senderId = "alice-uuid",
                    content = "hi there",
                    contentType = "text",
                    timestamp = 42L,
                    flushAckImmediately = false,
                )
            }
            // The envelope itself is still acked under its own (server) ids,
            // independent of the payload's — see MessageRepository.ackEnvelope.
            coVerify {
                messageRepository.ackEnvelope(
                    conversationId = "env-conv",
                    messageId = "env-msg",
                    flushAckImmediately = true,
                )
            }
        }

    @Test
    fun sealed_success_with_blank_payload_ids_falls_back_to_the_envelope_s_ids() =
        runTest {
            val payload = InnerPayload(contentType = "text", content = "hi".toByteArray())
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 1L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = payload.encode(),
                )

            useCase.receive(bytes, EnvelopeKind.SEALED, 1L, declaredSender = null, envelopeContext = ctx)

            coVerify {
                messageRepository.insertDecryptedMessage(
                    conversationId = "env-conv",
                    messageId = "env-msg",
                    senderId = "alice-uuid",
                    content = "hi",
                    contentType = "text",
                    timestamp = 1L,
                    flushAckImmediately = false,
                )
            }
        }

    @Test
    fun sealed_success_with_a_control_payload_does_not_persist_a_message_but_still_acks() =
        runTest {
            val payload = InnerPayload(conversationId = "payload-conv", contentType = "receipt/v1", content = "x".toByteArray())
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 1L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = payload.encode(),
                )

            val result = useCase.receive(bytes, EnvelopeKind.SEALED, 1L, declaredSender = null, envelopeContext = ctx)

            assertTrue(result is EnvelopeDecryptResult.Success)
            coVerify(exactly = 0) { messageRepository.insertDecryptedMessage(any(), any(), any(), any(), any(), any(), any()) }
            coVerify {
                messageRepository.ackEnvelope(
                    conversationId = "env-conv",
                    messageId = "env-msg",
                    flushAckImmediately = true,
                )
            }
        }

    @Test
    fun sealed_success_with_legacy_bare_utf8_plaintext_persists_as_a_user_message_with_the_envelope_s_content_type() =
        runTest {
            val legacyPlaintext = "hey there".toByteArray()
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 1L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = legacyPlaintext,
                )

            useCase.receive(bytes, EnvelopeKind.SEALED, 1L, declaredSender = null, envelopeContext = ctx)

            coVerify {
                messageRepository.insertDecryptedMessage(
                    conversationId = "env-conv",
                    messageId = "env-msg",
                    senderId = "alice-uuid",
                    content = "hey there",
                    contentType = "text",
                    timestamp = 1L,
                    flushAckImmediately = false,
                )
            }
        }

    @Test
    fun sealed_success_with_an_unrecognised_content_type_still_persists_as_a_user_message() =
        runTest {
            val payload =
                InnerPayload(
                    conversationId = "payload-conv",
                    messageId = "payload-msg",
                    contentType = "sticker/v9",
                    content = "surprise".toByteArray(),
                )
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 1L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = payload.encode(),
                )

            useCase.receive(bytes, EnvelopeKind.SEALED, 1L, declaredSender = null, envelopeContext = ctx)

            coVerify {
                messageRepository.insertDecryptedMessage(
                    conversationId = "payload-conv",
                    messageId = "payload-msg",
                    senderId = "alice-uuid",
                    content = "surprise",
                    contentType = "sticker/v9",
                    timestamp = 1L,
                    flushAckImmediately = false,
                )
            }
        }
}
