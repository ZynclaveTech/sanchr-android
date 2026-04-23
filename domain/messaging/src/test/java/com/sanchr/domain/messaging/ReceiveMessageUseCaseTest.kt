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
}
