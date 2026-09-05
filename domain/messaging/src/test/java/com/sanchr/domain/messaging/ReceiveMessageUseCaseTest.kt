package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.crypto.sealed.SealedSenderCipher
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.MessageStatus
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.UntrustedIdentityException
import sanchr.messaging.Messaging

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

    private val profileResolver = mockk<ContactProfileResolver>(relaxed = true)
    private val sessionManager = mockk<SessionManager> { every { getUserId() } returns "self-uuid" }

    private val useCase =
        ReceiveMessageUseCase(
            sealedSenderCipher,
            signalSessionManager,
            quarantineUseCase,
            messageRepositoryLazy,
            dispatchers,
            profileResolver,
            sessionManager,
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
            // stageAck = false: the row is keyed on the payload's id (a v4
            // UUID), which the server's delivery queue would not recognize
            // as an ack target — so this call must not also stage one.
            coVerify {
                messageRepository.insertDecryptedMessage(
                    conversationId = "payload-conv",
                    messageId = "payload-msg",
                    senderId = "alice-uuid",
                    content = "hi there",
                    contentType = "text",
                    timestamp = 42L,
                    flushAckImmediately = false,
                    stageAck = false,
                )
            }
            // Exactly one ack is staged for the envelope, keyed on its own
            // (server) ids — never the payload's. This is the property the
            // stageAck = false above exists to protect: a resend-safe row
            // id must not also produce a second, server-rejected ack entry.
            coVerify(exactly = 1) {
                messageRepository.ackEnvelope(
                    conversationId = "env-conv",
                    messageId = "env-msg",
                    flushAckImmediately = true,
                )
            }
            coVerify(exactly = 0) { messageRepository.ackEnvelope("payload-conv", "payload-msg", any()) }
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
                    stageAck = false,
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
            coVerify(exactly = 0) { messageRepository.insertDecryptedMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
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
                    stageAck = false,
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
                    stageAck = false,
                )
            }
        }

    // ── Task 3: applying an inbound sealed read receipt ──

    @Test
    fun `a sender's disappearing timer becomes a deadline anchored to the server timestamp`() =
        runTest {
            val payload =
                InnerPayload(
                    conversationId = "payload-conv",
                    messageId = "payload-msg",
                    contentType = "text",
                    content = "burn after reading".toByteArray(),
                    expiresAfterSecs = 3_600L,
                )
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 42L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = payload.encode(),
                )

            useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            // 42 (server timestamp) + 3600s, NOT "now + 3600s". A device that
            // syncs a week late must inherit the remaining lifetime, not a
            // fresh one — the property that makes the timer meaningful.
            coVerify {
                messageRepository.insertDecryptedMessage(
                    conversationId = "payload-conv",
                    messageId = "payload-msg",
                    senderId = "alice-uuid",
                    content = "burn after reading",
                    contentType = "text",
                    timestamp = 42L,
                    flushAckImmediately = false,
                    stageAck = false,
                    expiresAtMillis = 42L + 3_600_000L,
                )
            }
        }

    @Test
    fun `a message with no timer is persisted with no deadline`() =
        runTest {
            val payload =
                InnerPayload(
                    conversationId = "payload-conv",
                    messageId = "payload-msg",
                    contentType = "text",
                    content = "keep me".toByteArray(),
                )
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 42L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = payload.encode(),
                )

            useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            coVerify {
                messageRepository.insertDecryptedMessage(
                    conversationId = "payload-conv",
                    messageId = "payload-msg",
                    senderId = "alice-uuid",
                    content = "keep me",
                    contentType = "text",
                    timestamp = 42L,
                    flushAckImmediately = false,
                    stageAck = false,
                    expiresAtMillis = null,
                )
            }
        }

    @Test
    fun `a blocked sender's message is dropped before it is written`() =
        runTest {
            val payload =
                InnerPayload(
                    conversationId = "payload-conv",
                    messageId = "payload-msg",
                    contentType = "text",
                    content = "let me back in".toByteArray(),
                )
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 42L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "blocked-uuid",
                    senderDeviceId = 1,
                    plaintext = payload.encode(),
                )
            coEvery { messageRepository.isSenderBlocked("blocked-uuid") } returns true

            val result = useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            assertTrue(result is EnvelopeDecryptResult.Success)
            // No row means no transcript entry, no conversation-list preview,
            // no unread bump, and no notification (which is raised by
            // observing inserted rows).
            coVerify(exactly = 0) {
                messageRepository.insertDecryptedMessage(any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `a blocked sender's envelope is still acked so it is not redelivered forever`() =
        runTest {
            val payload =
                InnerPayload(
                    conversationId = "payload-conv",
                    messageId = "payload-msg",
                    contentType = "text",
                    content = "let me back in".toByteArray(),
                )
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 42L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "blocked-uuid",
                    senderDeviceId = 1,
                    plaintext = payload.encode(),
                )
            coEvery { messageRepository.isSenderBlocked("blocked-uuid") } returns true

            useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            // The block is ours, not the server's: it will keep redelivering
            // until acked, and the queue would never drain.
            coVerify(exactly = 1) {
                messageRepository.ackEnvelope(
                    conversationId = "env-conv",
                    messageId = "env-msg",
                    flushAckImmediately = any(),
                )
            }
        }

    @Test
    fun `an unblocked sender is unaffected`() =
        runTest {
            val payload =
                InnerPayload(
                    conversationId = "payload-conv",
                    messageId = "payload-msg",
                    contentType = "text",
                    content = "hello".toByteArray(),
                )
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 42L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = payload.encode(),
                )
            coEvery { messageRepository.isSenderBlocked("alice-uuid") } returns false

            useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            coVerify(exactly = 1) {
                messageRepository.insertDecryptedMessage(
                    conversationId = "payload-conv",
                    messageId = "payload-msg",
                    senderId = "alice-uuid",
                    content = "hello",
                    contentType = "text",
                    timestamp = 42L,
                    flushAckImmediately = false,
                    stageAck = false,
                    expiresAtMillis = null,
                )
            }
        }

    private fun receiptPlaintext(
        messageId: String,
        status: String,
        conversationId: String = "",
    ): ByteArray {
        val receipt =
            Messaging.ReceiptUpdate
                .newBuilder()
                .setConversationId(conversationId)
                .setMessageId(messageId)
                .setRecipientId("peer-uuid")
                .setStatus(status)
                .setTimestamp(12345L)
                .build()
        // Envelope ids are deliberately left empty by the sender (Task 2) —
        // the real ids travel only inside the serialized ReceiptUpdate.
        val payload =
            InnerPayload(
                conversationId = "",
                messageId = null,
                contentType = "receipt/v1",
                content = receipt.toByteArray(),
            )
        return payload.encode()
    }

    @Test
    fun sealed_success_with_a_well_formed_receipt_updates_the_named_message_s_status_and_still_acks() =
        runTest {
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 1L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = receiptPlaintext(messageId = "inner-msg", status = "read"),
                )

            useCase.receive(bytes, EnvelopeKind.SEALED, 1L, declaredSender = null, envelopeContext = ctx)

            // The status update is keyed on the id decoded out of the
            // protobuf ("inner-msg"), never the envelope's own id
            // ("env-msg") — the envelope's ids are deliberately empty for a
            // receipt, so reaching for ctx here would be a bug.
            coVerify(exactly = 1) { messageRepository.applyReceiptStatus("inner-msg", MessageStatus.READ) }
            coVerify(exactly = 0) { messageRepository.insertDecryptedMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 1) {
                messageRepository.ackEnvelope(
                    conversationId = "env-conv",
                    messageId = "env-msg",
                    flushAckImmediately = true,
                )
            }
        }

    @Test
    fun sealed_success_with_an_unrecognised_receipt_status_is_ignored_but_still_acks() =
        runTest {
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 1L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = receiptPlaintext(messageId = "inner-msg", status = "seen"),
                )

            useCase.receive(bytes, EnvelopeKind.SEALED, 1L, declaredSender = null, envelopeContext = ctx)

            coVerify(exactly = 0) { messageRepository.applyReceiptStatus(any(), any()) }
            coVerify(exactly = 1) {
                messageRepository.ackEnvelope(
                    conversationId = "env-conv",
                    messageId = "env-msg",
                    flushAckImmediately = true,
                )
            }
        }

    @Test
    fun sealed_success_with_malformed_receipt_content_does_not_throw_and_still_acks() =
        runTest {
            val payload =
                InnerPayload(
                    conversationId = "",
                    messageId = null,
                    contentType = "receipt/v1",
                    // field 1, wire type 7 — wire type 7 is not a valid
                    // protobuf wire type, so parseFrom must throw
                    // InvalidProtocolBufferException on this byte.
                    content = byteArrayOf(0x0F),
                )
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 1L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = payload.encode(),
                )

            val result = useCase.receive(bytes, EnvelopeKind.SEALED, 1L, declaredSender = null, envelopeContext = ctx)

            assertTrue(result is EnvelopeDecryptResult.Success)
            coVerify(exactly = 0) { messageRepository.applyReceiptStatus(any(), any()) }
            coVerify(exactly = 1) {
                messageRepository.ackEnvelope(
                    conversationId = "env-conv",
                    messageId = "env-msg",
                    flushAckImmediately = true,
                )
            }
        }

    @Test
    fun sealed_success_with_a_blank_receipt_message_id_is_ignored_but_still_acks() =
        runTest {
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 1L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = receiptPlaintext(messageId = "", status = "read"),
                )

            useCase.receive(bytes, EnvelopeKind.SEALED, 1L, declaredSender = null, envelopeContext = ctx)

            coVerify(exactly = 0) { messageRepository.applyReceiptStatus(any(), any()) }
            coVerify(exactly = 1) {
                messageRepository.ackEnvelope(
                    conversationId = "env-conv",
                    messageId = "env-msg",
                    flushAckImmediately = true,
                )
            }
        }

    @Test
    fun sealed_success_with_a_status_write_failure_still_acks_and_returns_success() =
        runTest {
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 1L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = receiptPlaintext(messageId = "inner-msg", status = "read"),
                )
            coEvery {
                messageRepository.applyReceiptStatus("inner-msg", MessageStatus.READ)
            } throws RuntimeException("SQLiteException: disk I/O error")

            val result = useCase.receive(bytes, EnvelopeKind.SEALED, 1L, declaredSender = null, envelopeContext = ctx)

            // An unacked envelope is redelivered forever — a status-write
            // failure must never prevent the ack, and must never be
            // mislabelled as a crypto failure (Quarantined).
            assertTrue(result is EnvelopeDecryptResult.Success)
            coVerify(exactly = 1) {
                messageRepository.ackEnvelope(
                    conversationId = "env-conv",
                    messageId = "env-msg",
                    flushAckImmediately = true,
                )
            }
        }

    @Test
    fun cancellation_from_apply_receipt_status_escapes_receive_rather_than_being_swallowed() =
        runTest {
            // This exact class of bug — a broad catch absorbing
            // CancellationException instead of rethrowing it — has been
            // fixed multiple times on this project already. Pinned
            // directly rather than trusted by convention, so the guard
            // added for the write-failure test above can never be widened
            // into swallowing cancellation without this test going red.
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 1L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = receiptPlaintext(messageId = "inner-msg", status = "read"),
                )
            coEvery {
                messageRepository.applyReceiptStatus("inner-msg", MessageStatus.READ)
            } throws CancellationException("scope cancelled")

            assertFailsWith<CancellationException> {
                useCase.receive(bytes, EnvelopeKind.SEALED, 1L, declaredSender = null, envelopeContext = ctx)
            }
        }
}
