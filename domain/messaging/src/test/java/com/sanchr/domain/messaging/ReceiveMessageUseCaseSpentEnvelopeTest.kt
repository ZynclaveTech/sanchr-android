package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.crypto.sealed.SealedSenderCipher
import com.sanchr.core.crypto.sealed.TypedEnvelopeCipher
import com.sanchr.core.datastore.SessionManager
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.UntrustedIdentityException

/**
 * An envelope that will never be filed is acked, so the server stops sending
 * it. One that still might be is left.
 */
class ReceiveMessageUseCaseSpentEnvelopeTest {
    private val sealedSenderCipher = mockk<SealedSenderCipher>()
    private val quarantine = mockk<QuarantineEnvelopeUseCase>(relaxed = true)
    private val messageRepository =
        mockk<MessageRepository>(relaxed = true) {
            coEvery { mayPostTo(any(), any()) } returns true
        }
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }

    private val staleSessions = mockk<StaleSessionHealer>(relaxed = true)

    private val useCase =
        ReceiveMessageUseCase(
            sealedSenderCipher,
            mockk<TypedEnvelopeCipher>(relaxed = true),
            mockk<SignalSessionManager>(relaxed = true),
            quarantine,
            Lazy<MessageRepository> { messageRepository },
            dispatchers,
            mockk<ContactProfileResolver>(relaxed = true),
            mockk<SessionManager> { every { getUserId() } returns "self-uuid" },
            PresenceStore(),
            SessionRebuildThrottle(),
            mockk<DeliveryReceiptDispatcher>(relaxed = true),
            staleSessions,
        )

    private val bytes = byteArrayOf(10, 20, 30)
    private val ctx = IncomingEnvelopeContext(conversationId = "env-conv", messageId = "env-msg", contentType = "text")

    private suspend fun receiveFailingWith(error: Exception): EnvelopeDecryptResult {
        coEvery { sealedSenderCipher.sealedDecrypt(bytes, 42L) } throws error
        return useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)
    }

    private fun acked(times: Int) = coVerify(exactly = times) { messageRepository.ackEnvelope("env-conv", "env-msg", any()) }

    /** Already dealt with; the server sent it again at every sync for thirty days. */
    @Test
    fun `a duplicate is acked`() =
        runTest {
            assertTrue(receiveFailingWith(DuplicateMessageException("seen")) is EnvelopeDecryptResult.DuplicateMessage)

            acked(1)
        }

    @Test
    fun `a malformed envelope is quarantined and acked`() =
        runTest {
            val result = receiveFailingWith(InvalidMessageException("bad mac"))

            assertTrue(result is EnvelopeDecryptResult.Quarantined)
            acked(1)
        }

    /** The rebuild under way may yet answer it. */
    @Test
    fun `an envelope with no session is left for the server to send again`() =
        runTest {
            assertTrue(receiveFailingWith(NoSessionException("none")) is EnvelopeDecryptResult.SessionMissing)

            acked(0)
        }

    /** It waits on the user deciding to trust the new identity. */
    @Test
    fun `an envelope from a changed identity is left`() =
        runTest {
            val result = receiveFailingWith(UntrustedIdentityException("alice"))

            assertTrue(result is EnvelopeDecryptResult.Quarantined)
            acked(0)
        }

    @Test
    fun `a failure of an unknown kind is left`() =
        runTest {
            val result = receiveFailingWith(IllegalStateException("who knows"))

            assertTrue(result is EnvelopeDecryptResult.Quarantined)
            acked(0)
        }

    @Test
    fun `without an envelope's ids there is nothing to ack`() =
        runTest {
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 42L) } throws DuplicateMessageException("seen")

            useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null)

            coVerify(exactly = 0) { messageRepository.ackEnvelope(any(), any(), any()) }
        }

    /**
     * It opens a session with a key that is gone and no session here reads
     * it, so it never will be read: spent. And its sender is still writing on
     * a session this device has lost, which only a re-key ends.
     */
    @Test
    fun `an envelope naming a spent pre-key is acked and sets a re-key going`() =
        runTest {
            val result = receiveFailingWith(InvalidKeyIdException("No pre-key found for ID: 1"))

            assertTrue(result is EnvelopeDecryptResult.Quarantined)
            acked(1)
            verify(exactly = 1) { staleSessions.unreadableMessageArrived() }
        }

    @Test
    fun `no other failure sets a re-key going`() =
        runTest {
            receiveFailingWith(InvalidMessageException("bad mac"))
            receiveFailingWith(DuplicateMessageException("seen"))

            verify(exactly = 0) { staleSessions.unreadableMessageArrived() }
        }
}
