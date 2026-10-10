package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.profile.ProfileKeyStore
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.proto.messaging.SealedDeviceMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import sanchr.messaging.Messaging

class SendPresenceUseCaseTest {
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }
    private val sender = mockk<SendMessageUseCase> { coEvery { sendControl(any(), any()) } returns Unit }
    private val sessionManager = mockk<SessionManager> { every { getUserId() } returns "self" }
    private val prefs = mockk<UserPreferences>()
    private val keys = mockk<ProfileKeyStore> { every { ownProfileKey() } returns ByteArray(32) { 1 } }
    private val useCase = SendPresenceUseCase(sender, sessionManager, prefs, keys, dispatchers)

    private fun captureSent(): InnerPayload {
        val plaintext = slot<ByteArray>()
        coEvery { sender.encryptAndWrapDeviceMessages(capture(plaintext), listOf("peer"), "", SealedSendKind.Control) } returns
            listOf(mockk<SealedDeviceMessage>())
        return InnerPayload.decode(plaintext.captured)!!
    }

    @Test
    fun `sends an ONLINE PresenceUpdate as a sealed control payload carrying our profile key`() =
        runTest {
            every { prefs.onlineStatusVisible } returns flowOf(true)
            val plaintext = slot<ByteArray>()
            coEvery { sender.encryptAndWrapDeviceMessages(capture(plaintext), listOf("peer"), "", SealedSendKind.Control) } returns
                listOf(mockk())

            useCase("peer", PresenceStatus.ONLINE)

            val payload = InnerPayload.decode(plaintext.captured)!!
            assertEquals("presence/v1", payload.contentType)
            val update = Messaging.PresenceUpdate.parseFrom(payload.content)
            assertEquals(Messaging.PresenceStatus.ONLINE, update.statusCode)
            assertEquals("self", update.userId)
            assertEquals(0L, update.lastSeen)
            assertEquals(32, payload.senderProfileKey!!.size)
            coVerify { sender.sendControl(any(), any()) }
        }

    @Test
    fun `with online status off the peer is told HIDDEN, and offline carries a last-seen`() =
        runTest {
            every { prefs.onlineStatusVisible } returns flowOf(false)
            val plaintext = slot<ByteArray>()
            coEvery { sender.encryptAndWrapDeviceMessages(capture(plaintext), listOf("peer"), "", SealedSendKind.Control) } returns
                listOf(mockk())
            useCase("peer", PresenceStatus.ONLINE)
            assertEquals(
                Messaging.PresenceStatus.HIDDEN,
                Messaging.PresenceUpdate.parseFrom(InnerPayload.decode(plaintext.captured)!!.content).statusCode,
            )

            every { prefs.onlineStatusVisible } returns flowOf(true)
            useCase("peer", PresenceStatus.OFFLINE)
            val update = Messaging.PresenceUpdate.parseFrom(InnerPayload.decode(plaintext.captured)!!.content)
            assertEquals(Messaging.PresenceStatus.OFFLINE, update.statusCode)
            assert(update.lastSeen > 0)
        }

    @Test
    fun `no key-capable devices means no token is spent and nothing is sent`() =
        runTest {
            every { prefs.onlineStatusVisible } returns flowOf(true)
            coEvery { sender.encryptAndWrapDeviceMessages(any(), any(), any(), any()) } returns emptyList()
            useCase("peer", PresenceStatus.ONLINE)
            coVerify(exactly = 0) { sender.sendControl(any(), any()) }
        }
}
