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
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ReceiveMessageUseCase]'s Profile Key harvesting: every sealed payload from
 * a peer carries their key, a `profile-key/v1` control carries it as raw
 * content, and none of it may ever fail the receive. Split from
 * [ReceiveMessageUseCaseTest] to keep that class within bounds.
 */
class ReceiveMessageUseCaseProfileKeyTest {
    private val sealedSenderCipher = mockk<SealedSenderCipher>()
    private val signalSessionManager = mockk<SignalSessionManager>(relaxed = true)
    private val quarantineUseCase = mockk<QuarantineEnvelopeUseCase>(relaxed = true)
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
            PresenceStore(),
        )

    private val bytes = byteArrayOf(10, 20, 30)
    private val ctx = IncomingEnvelopeContext(conversationId = "env-conv", messageId = "env-msg", contentType = "text")

    private val peerKey = ByteArray(32) { 0x42 }

    private fun stubSealed(
        payload: InnerPayload,
        sender: String = "alice-uuid",
    ) {
        coEvery { sealedSenderCipher.sealedDecrypt(bytes, 42L) } returns
            SealedSenderCipher.DecryptedEnvelope(senderUserId = sender, senderDeviceId = 1, plaintext = payload.encode())
    }

    @Test
    fun `a new profile key on a user message is recorded and resolved, and the message still persists`() =
        runTest {
            every { profileResolver.recordProfileKey("alice-uuid", any()) } returns true
            stubSealed(InnerPayload(conversationId = "c", messageId = "m", content = "hi".toByteArray(), senderProfileKey = peerKey))

            useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            verify { profileResolver.recordProfileKey("alice-uuid", match { it.contentEquals(peerKey) }) }
            coVerify { profileResolver.refresh("alice-uuid") }
            coVerify { messageRepository.insertDecryptedMessage(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `an unchanged profile key is not re-resolved`() =
        runTest {
            every { profileResolver.recordProfileKey("alice-uuid", any()) } returns false
            stubSealed(InnerPayload(conversationId = "c", content = "hi".toByteArray(), senderProfileKey = peerKey))

            useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            coVerify(exactly = 0) { profileResolver.refresh(any()) }
        }

    @Test
    fun `a profile-key v1 control carries the key as raw content and is acked without persisting`() =
        runTest {
            every { profileResolver.recordProfileKey("alice-uuid", any()) } returns true
            stubSealed(InnerPayload(conversationId = "c", contentType = "profile-key/v1", content = peerKey))

            useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            verify { profileResolver.recordProfileKey("alice-uuid", match { it.contentEquals(peerKey) }) }
            coVerify { profileResolver.refresh("alice-uuid") }
            coVerify(exactly = 0) {
                messageRepository.insertDecryptedMessage(any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
            coVerify { messageRepository.ackEnvelope(ctx.conversationId, ctx.messageId, true) }
        }

    @Test
    fun `our own key on a sync payload from another of our devices is ignored`() =
        runTest {
            stubSealed(
                InnerPayload(conversationId = "c", content = "hi".toByteArray(), isSync = true, senderProfileKey = peerKey),
                sender = "self-uuid",
            )

            useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            verify(exactly = 0) { profileResolver.recordProfileKey(any(), any()) }
        }

    @Test
    fun `a key of the wrong length is ignored`() =
        runTest {
            stubSealed(InnerPayload(conversationId = "c", content = "hi".toByteArray(), senderProfileKey = ByteArray(16)))

            useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            verify(exactly = 0) { profileResolver.recordProfileKey(any(), any()) }
        }

    @Test
    fun `a payload without a key records nothing`() =
        runTest {
            stubSealed(InnerPayload(conversationId = "c", content = "hi".toByteArray()))

            useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            verify(exactly = 0) { profileResolver.recordProfileKey(any(), any()) }
        }

    @Test
    fun `a broken key store or resolver never fails the receive`() =
        runTest {
            every { profileResolver.recordProfileKey(any(), any()) } throws IllegalStateException("keystore down")
            stubSealed(InnerPayload(conversationId = "c", messageId = "m", content = "hi".toByteArray(), senderProfileKey = peerKey))

            val result = useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            assertTrue(result is EnvelopeDecryptResult.Success)
            coVerify { messageRepository.insertDecryptedMessage(any(), any(), any(), any(), any(), any(), any(), any(), any()) }

            every { profileResolver.recordProfileKey(any(), any()) } returns true
            coEvery { profileResolver.refresh(any()) } throws IllegalStateException("network down")
            val second = useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)
            assertTrue(second is EnvelopeDecryptResult.Success)
        }
}
