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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sanchr.messaging.Messaging

class ReceiveMessageUseCasePresenceTest {
    private val sealedSenderCipher = mockk<SealedSenderCipher>()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }
    private var now = 1_000_000L
    private val presenceStore = PresenceStore { now }
    private val useCase =
        ReceiveMessageUseCase(
            sealedSenderCipher,
            mockk<SignalSessionManager>(relaxed = true),
            mockk<QuarantineEnvelopeUseCase>(relaxed = true),
            Lazy<MessageRepository> { messageRepository },
            dispatchers,
            mockk<ContactProfileResolver>(relaxed = true),
            mockk<SessionManager> { every { getUserId() } returns "self" },
            presenceStore,
        )
    private val bytes = byteArrayOf(1)
    private val ctx = IncomingEnvelopeContext(conversationId = "c", messageId = "m", contentType = "text")

    private fun stub(payload: InnerPayload) {
        coEvery { sealedSenderCipher.sealedDecrypt(bytes, 7L) } returns
            SealedSenderCipher.DecryptedEnvelope(senderUserId = "alice", senderDeviceId = 1, plaintext = payload.encode())
    }

    private fun presence(
        status: Messaging.PresenceStatus,
        lastSeen: Long = 0,
        claimedUser: String = "alice",
    ) = InnerPayload(
        conversationId = "",
        contentType = "presence/v1",
        content =
            Messaging.PresenceUpdate
                .newBuilder()
                .setUserId(
                    claimedUser,
                ).setStatusCode(status)
                .setLastSeen(lastSeen)
                .build()
                .toByteArray(),
    )

    @Test
    fun `an iOS presence payload updates the store for the envelope's sender, is acked, and is never a message`() =
        runTest {
            stub(presence(Messaging.PresenceStatus.ONLINE))

            useCase.receive(bytes, EnvelopeKind.SEALED, 7L, declaredSender = null, envelopeContext = ctx)

            assertEquals("Online", presenceStore.statusLine("alice"))
            coVerify(
                exactly = 0,
            ) { messageRepository.insertDecryptedMessage(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify { messageRepository.ackEnvelope("c", "m", true) }
        }

    @Test
    fun `offline carries last seen, hidden shows nothing, and the payload cannot speak for another user`() =
        runTest {
            stub(presence(Messaging.PresenceStatus.OFFLINE, lastSeen = now - 5 * 60_000, claimedUser = "bob"))
            useCase.receive(bytes, EnvelopeKind.SEALED, 7L, declaredSender = null, envelopeContext = ctx)
            assertEquals("Last seen 5 min ago", presenceStore.statusLine("alice"))
            assertNull(presenceStore.statusLine("bob"))

            stub(presence(Messaging.PresenceStatus.HIDDEN))
            useCase.receive(bytes, EnvelopeKind.SEALED, 7L, declaredSender = null, envelopeContext = ctx)
            assertNull(presenceStore.statusLine("alice"))
        }

    @Test
    fun `garbage presence content is ignored and still acked`() =
        runTest {
            stub(InnerPayload(conversationId = "", contentType = "presence/v1", content = byteArrayOf(-1, -1, -1)))
            useCase.receive(bytes, EnvelopeKind.SEALED, 7L, declaredSender = null, envelopeContext = ctx)
            assertNull(presenceStore.statusLine("alice"))
            coVerify { messageRepository.ackEnvelope("c", "m", true) }
        }
}
