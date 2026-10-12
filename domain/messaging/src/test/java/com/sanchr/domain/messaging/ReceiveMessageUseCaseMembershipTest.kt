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

    /**
     * The first message of a chat arrives before the chat does. The
     * conversation is made ready first; only then is it asked who may post
     * to it, and only then is the message saved.
     */
    @Test
    fun `the conversation is made ready before it is asked about or saved into`() =
        runTest {
            val payload =
                InnerPayload(
                    conversationId = "payload-conv",
                    messageId = "payload-msg",
                    contentType = "text",
                    content = "first hello".toByteArray(),
                )
            coEvery { sealedSenderCipher.sealedDecrypt(bytes, 42L) } returns
                SealedSenderCipher.DecryptedEnvelope(
                    senderUserId = "alice-uuid",
                    senderDeviceId = 1,
                    plaintext = payload.encode(),
                )

            useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            io.mockk.coVerifyOrder {
                messageRepository.prepareConversationForIncoming("payload-conv", "payload-msg", "alice-uuid", 42L)
                messageRepository.mayPostTo("payload-conv", "alice-uuid")
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

    // ── A message that decrypted but could not be filed ──────────────────

    private fun aMessageFromAlice() {
        val payload =
            InnerPayload(
                conversationId = "payload-conv",
                messageId = "payload-msg",
                contentType = "text",
                content = "do not lose me".toByteArray(),
            )
        coEvery { sealedSenderCipher.sealedDecrypt(bytes, 42L) } returns
            SealedSenderCipher.DecryptedEnvelope(senderUserId = "alice-uuid", senderDeviceId = 1, plaintext = payload.encode())
    }

    /**
     * The envelope cannot be decrypted twice. What came out of it used to be
     * dropped when the save failed; it has to be kept instead.
     */
    @Test
    fun `a message that cannot be saved is held, with what it said, and its envelope acked`() =
        runTest {
            aMessageFromAlice()
            coEvery {
                messageRepository.insertDecryptedMessage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws java.io.IOException("disk full")
            val held = io.mockk.slot<HeldIncoming>()

            val result = useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            assertTrue(result is EnvelopeDecryptResult.Success)
            coVerify(exactly = 1) { messageRepository.holdIncoming(capture(held)) }
            assertTrue(String(InnerPayload.decode(held.captured.decrypted.plaintext)!!.content).contains("do not lose me"))
            kotlin.test.assertEquals("alice-uuid", held.captured.decrypted.senderUserId)
            kotlin.test.assertEquals(ctx, held.captured.envelope)
            // Sending it again cannot help: it will not decrypt a second time.
            coVerify(exactly = 1) { messageRepository.ackEnvelope(ctx.conversationId, ctx.messageId, any()) }
        }

    @Test
    fun `a message that is saved is not held`() =
        runTest {
            aMessageFromAlice()

            useCase.receive(bytes, EnvelopeKind.SEALED, 42L, declaredSender = null, envelopeContext = ctx)

            coVerify(exactly = 0) { messageRepository.holdIncoming(any()) }
        }

    @Test
    fun `filing a held message goes through the same path a new one does`() =
        runTest {
            val payload =
                InnerPayload(
                    conversationId = "payload-conv",
                    messageId = "payload-msg",
                    contentType = "text",
                    content = "later".toByteArray(),
                )
            val decrypted = EnvelopeDecryptResult.Success("alice-uuid", 1, payload.encode(), 42L)

            useCase.file(decrypted, ctx)

            io.mockk.coVerifyOrder {
                messageRepository.prepareConversationForIncoming("payload-conv", "payload-msg", "alice-uuid", 42L)
                messageRepository.mayPostTo("payload-conv", "alice-uuid")
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
