package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.crypto.sealed.SealedSenderCipher
import com.sanchr.core.datastore.SessionManager
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.signal.libsignal.protocol.NoSessionException

/**
 * A message that cannot be decrypted for want of a session is answered by
 * starting a new one with its sender. Once — each rebuild fetches a bundle,
 * and each bundle takes one of the sender's one-time pre-keys.
 */
class ReceiveMessageUseCaseSessionRebuildTest {
    private val sealedSenderCipher = mockk<SealedSenderCipher>()
    private val typedEnvelopeCipher = mockk<com.sanchr.core.crypto.sealed.TypedEnvelopeCipher>(relaxed = true)
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
            typedEnvelopeCipher,
            signalSessionManager,
            quarantineUseCase,
            messageRepositoryLazy,
            dispatchers,
            profileResolver,
            sessionManager,
            PresenceStore(),
        )

    private val bytes = byteArrayOf(10, 20, 30)
    private val senderHint = ServerProvidedSender("bob-uuid", 2)

    @Test
    fun a_burst_of_undecryptable_messages_from_one_device_rebuilds_its_session_once() =
        runTest {
            coEvery { signalSessionManager.decrypt(any(), any(), any()) } throws NoSessionException("no session")
            coEvery { signalSessionManager.establishSession("bob-uuid", 2) } returns mockk(relaxed = true)

            repeat(10) { useCase.receive(bytes, EnvelopeKind.NON_SEALED, 42L, senderHint) }

            coVerify(exactly = 1) { signalSessionManager.establishSession("bob-uuid", 2) }
        }

    @Test
    fun the_same_message_delivered_again_does_not_rebuild_again() =
        runTest {
            // It is left on the server while undecryptable, so every sync
            // brings it back.
            coEvery { signalSessionManager.decrypt(any(), any(), any()) } throws NoSessionException("no session")
            coEvery { signalSessionManager.establishSession(any(), any()) } throws IllegalStateException("offline")

            useCase.receive(bytes, EnvelopeKind.NON_SEALED, 42L, senderHint)
            useCase.receive(bytes, EnvelopeKind.NON_SEALED, 43L, senderHint)

            // A failed attempt has fetched a bundle just the same.
            coVerify(exactly = 1) { signalSessionManager.establishSession("bob-uuid", 2) }
        }

    @Test
    fun another_senders_failure_is_not_held_back_by_the_first() =
        runTest {
            coEvery { signalSessionManager.decrypt(any(), any(), any()) } throws NoSessionException("no session")
            coEvery { signalSessionManager.establishSession(any(), any()) } returns mockk(relaxed = true)

            useCase.receive(bytes, EnvelopeKind.NON_SEALED, 42L, senderHint)
            useCase.receive(bytes, EnvelopeKind.NON_SEALED, 42L, ServerProvidedSender("carol-uuid", 1))

            coVerify(exactly = 1) { signalSessionManager.establishSession("bob-uuid", 2) }
            coVerify(exactly = 1) { signalSessionManager.establishSession("carol-uuid", 1) }
        }
}
