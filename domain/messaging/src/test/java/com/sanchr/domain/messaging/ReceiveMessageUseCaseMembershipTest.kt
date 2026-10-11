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
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

/** A received message is filed only where its sender may post. */
class ReceiveMessageUseCaseMembershipTest {
    private val sealedSenderCipher = mockk<SealedSenderCipher>()
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

    private val useCase =
        ReceiveMessageUseCase(
            sealedSenderCipher,
            mockk<TypedEnvelopeCipher>(relaxed = true),
            mockk<SignalSessionManager>(),
            mockk<QuarantineEnvelopeUseCase>(),
            Lazy<MessageRepository> { messageRepository },
            dispatchers,
            mockk<ContactProfileResolver>(relaxed = true),
            mockk<SessionManager> { every { getUserId() } returns "self-uuid" },
            PresenceStore(),
            SessionRebuildThrottle(),
        )

    private val bytes = byteArrayOf(10, 20, 30)
    private val ctx = IncomingEnvelopeContext(conversationId = "env-conv", messageId = "env-msg", contentType = "text")

    /**
     * The payload names its own conversation. Mallory's message, naming this
     * user's chat with Alice, must not be filed there.
     */
    @Test
    fun `a message naming a one-to-one chat its sender is not in is not saved, and is acked`() =
        runTest {
            val payload =
                InnerPayload(
                    conversationId = "chat-with-alice",
                    messageId = "payload-msg",
                    contentType = "text",
                    content = "it's me, Alice".toByteArray(),
                )
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 42L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "mallory-uuid",
                    senderDeviceId = 1,
                    plaintext = payload.encode(),
                )
            coEvery { messageRepository.mayPostTo("chat-with-alice", "mallory-uuid") } returns false

            val result = useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            assertTrue(result is EnvelopeDecryptResult.Success)
            coVerify(exactly = 0) {
                messageRepository.insertDecryptedMessage(any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
            // Under the envelope's ids, or it comes back for ever.
            coVerify(exactly = 1) { messageRepository.ackEnvelope(ctx.conversationId, ctx.messageId, any()) }
        }

    /** The conversation asked about is the one the message will be filed in, and the sender is the authenticated one. */
    @Test
    fun `membership is checked for the conversation the payload names and the sender the session proved`() =
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

            useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            coVerify(exactly = 1) { messageRepository.mayPostTo("payload-conv", "alice-uuid") }
            coVerify(exactly = 1) {
                messageRepository.insertDecryptedMessage(
                    "payload-conv",
                    "payload-msg",
                    "alice-uuid",
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }
        }
}
