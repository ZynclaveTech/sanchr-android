package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.crypto.profile.ProfileKeyStore
import com.sanchr.core.datastore.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.signal.libsignal.protocol.SignalProtocolAddress

/**
 * A contact still writing on a session this device has lost is moved onto a
 * new one, and not more often than the cooldown allows.
 */
class StaleSessionHealerTest {
    private val sessions = mockk<SignalSessionManager>(relaxed = true)
    private val sendMessage = mockk<SendMessageUseCase>(relaxed = true)
    private val profileKeys = mockk<ProfileKeyStore> { every { ownProfileKey() } returns ByteArray(32) { 7 } }
    private val sessionManager = mockk<SessionManager> { every { getUserId() } returns "self" }
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }
    private var clock = 1_000_000L
    private val healer =
        StaleSessionHealer(
            sessions = sessions,
            sendMessage = { sendMessage },
            profileKeys = profileKeys,
            sessionManager = sessionManager,
            throttle = SessionRebuildThrottle(cooldownMillis = COOLDOWN, now = { clock }),
            dispatchers = dispatchers,
        )

    private fun holding(vararg peers: String) {
        coEvery { sessions.sessionPeers() } returns peers.map { SignalProtocolAddress(it, 1) }
        coEvery { sendMessage.encryptAndWrapDeviceMessages(any(), any(), any(), any()) } returns listOf(mockk(relaxed = true))
    }

    @Test
    fun `each contact a session is held for is reset and sent to on a new one`() =
        runTest {
            holding("alice", "bob")

            healer.heal()

            coVerify(exactly = 1) { sessions.resetSession("alice", 1) }
            coVerify(exactly = 1) { sessions.resetSession("bob", 1) }
            coVerify(exactly = 1) { sendMessage.encryptAndWrapDeviceMessages(any(), listOf("alice"), "", SealedSendKind.Control) }
            coVerify(exactly = 1) { sendMessage.encryptAndWrapDeviceMessages(any(), listOf("bob"), "", SealedSendKind.Control) }
            coVerify(exactly = 2) { sendMessage.sendControl(any(), any()) }
        }

    /** Anyone can send a message that sets this off, so it cannot be made to run on every one. */
    @Test
    fun `a contact is re-keyed once per cooldown however many unreadable messages arrive`() =
        runTest {
            holding("alice")

            repeat(5) { healer.heal() }
            coVerify(exactly = 1) { sessions.resetSession("alice", 1) }

            clock += COOLDOWN + 1
            healer.heal()
            coVerify(exactly = 2) { sessions.resetSession("alice", 1) }
        }

    @Test
    fun `a session with this account itself is left alone`() =
        runTest {
            holding("self")

            healer.heal()

            coVerify(exactly = 0) { sessions.resetSession(any(), any()) }
        }

    @Test
    fun `one contact failing does not stop the rest`() =
        runTest {
            holding("alice", "bob")
            coEvery { sessions.resetSession("alice", 1) } throws IllegalStateException("boom")

            healer.heal()

            coVerify(exactly = 1) { sessions.resetSession("bob", 1) }
        }

    private companion object {
        const val COOLDOWN = 60_000L
    }
}
