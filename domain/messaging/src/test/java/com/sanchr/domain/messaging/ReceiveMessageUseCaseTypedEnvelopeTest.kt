package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.crypto.sealed.SealedSenderCipher
import com.sanchr.core.datastore.SessionManager
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.UntrustedIdentityException

/**
 * Which decrypt path an envelope takes.
 *
 * iOS sends a type byte and a plain Signal message; this app sends a
 * sealed-sender envelope. Until both were read, the two could not decrypt each
 * other at all. The first byte tells them apart, and each has to reach the
 * cipher that understands it — a typed envelope given to the padded path is
 * rejected for having no padding to strip.
 */
class ReceiveMessageUseCaseTypedEnvelopeTest {
    private val sealedSenderCipher = mockk<SealedSenderCipher>()
    private val typedEnvelopeCipher = mockk<com.sanchr.core.crypto.sealed.TypedEnvelopeCipher>(relaxed = true)
    private val signalSessionManager = mockk<SignalSessionManager>()
    private val quarantineUseCase = mockk<QuarantineEnvelopeUseCase>()
    private val messageRepository =
        mockk<MessageRepository>(relaxed = true) {
            // A relaxed mock answers false, which would turn every message away.
            coEvery { mayPostTo(any(), any()) } returns true
        }
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
            typedEnvelopeCipher,
            signalSessionManager,
            quarantineUseCase,
            messageRepositoryLazy,
            dispatchers,
            profileResolver,
            sessionManager,
            PresenceStore(),
            SessionRebuildThrottle(),
        )

    private val bytes = byteArrayOf(10, 20, 30)
    private val senderHint = ServerProvidedSender("bob-uuid", 2)

    private val typedFirst = byteArrayOf(0x01, 0x33, 0x0a)
    private val typedLater = byteArrayOf(0x02, 0x33, 0x0a)

    @Test
    fun a_typed_envelope_on_the_sealed_path_is_read_as_one_and_not_as_sealed_sender() =
        runTest {
            coEvery { typedEnvelopeCipher.decryptSealed(typedLater, any(), any()) } returns
                SealedSenderCipher.DecryptedEnvelope("alice-uuid", 3, byteArrayOf(7))

            val result = useCase.receive(typedLater, EnvelopeKind.SEALED, 42L, declaredSender = null)

            assertTrue(result is EnvelopeDecryptResult.Success)
            assertEquals("alice-uuid", result.senderUserId)
            assertEquals(3, result.senderDeviceId)
            coVerify(exactly = 0) { sealedSenderCipher.sealedDecrypt(any(), any()) }
        }

    @Test
    fun a_typed_envelope_with_a_named_sender_is_opened_with_that_senders_session() =
        runTest {
            coEvery { typedEnvelopeCipher.decryptFrom(typedFirst, "bob-uuid", 2) } returns byteArrayOf(9)

            val result = useCase.receive(typedFirst, EnvelopeKind.NON_SEALED, 42L, senderHint)

            assertTrue(result is EnvelopeDecryptResult.Success)
            assertEquals("bob-uuid", result.senderUserId)
            // Not through the padded path: iOS does not pad, so there is no
            // sentinel to strip and stripping would reject the message.
            coVerify(exactly = 0) { signalSessionManager.decrypt(any(), any(), any()) }
        }

    @Test
    fun this_apps_own_envelopes_still_take_the_paths_they_always_did() =
        runTest {
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 42L) } returns
                SealedSenderCipher.DecryptedEnvelope("alice-uuid", 1, byteArrayOf(1))
            coEvery { signalSessionManager.decrypt(bytes, "bob-uuid", 2) } returns byteArrayOf(2)

            useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null)
            useCase.receive(bytes, EnvelopeKind.NON_SEALED, 42L, senderHint)

            coVerify(exactly = 0) { typedEnvelopeCipher.decryptSealed(any(), any(), any()) }
            coVerify(exactly = 0) { typedEnvelopeCipher.decryptFrom(any(), any(), any()) }
        }

    @Test
    fun a_typed_first_message_whose_sender_cannot_be_vouched_for_is_quarantined_as_untrusted() =
        runTest {
            coEvery { typedEnvelopeCipher.decryptSealed(typedFirst, any(), any()) } throws
                UntrustedIdentityException("carol is not the holder of the key this message was sent with")
            coJustRun { quarantineUseCase.quarantine(any(), any(), any(), any(), any(), any(), any()) }

            val result = useCase.receive(typedFirst, EnvelopeKind.SEALED, 42L, declaredSender = null)

            assertTrue(result is EnvelopeDecryptResult.Quarantined)
            assertEquals(FailureClass.UNTRUSTED_IDENTITY, result.failureClass)
        }

    @Test
    fun a_typed_message_no_session_here_can_read_is_quarantined_rather_than_left_to_be_redelivered() =
        runTest {
            // The wording matters: "session" in this exception's text is read
            // as "rebuild and wait", which would leave an envelope nothing can
            // ever open on the server queue forever.
            coEvery { typedEnvelopeCipher.decryptSealed(typedLater, any(), any()) } throws
                InvalidMessageException("no ratchet held by this device authenticates the envelope")
            coJustRun { quarantineUseCase.quarantine(any(), any(), any(), any(), any(), any(), any()) }

            val result = useCase.receive(typedLater, EnvelopeKind.SEALED, 42L, declaredSender = null)

            assertTrue(result is EnvelopeDecryptResult.Quarantined)
            assertEquals(FailureClass.INVALID_MESSAGE, result.failureClass)
        }
}
