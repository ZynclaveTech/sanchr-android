package com.sanchr.core.crypto

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.PreKeyBundle

/**
 * Asking the server whose key something is costs the named user a one-time
 * pre-key, and the question is triggered by envelopes from people not yet
 * identified. It must not be askable without limit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class PublishedIdentityLookupTest {
    private val keyManager = mockk<SignalKeyManager>()
    private val manager =
        SignalSessionManager(
            store = mockk(relaxed = true),
            keyManager = keyManager,
            dispatchers = mockk(relaxed = true),
            sealedSenderCipher = mockk(relaxed = true),
            senderCertificateManager = mockk(relaxed = true),
            keyServiceClient = mockk(relaxed = true),
        )

    private fun publishing(
        userId: String,
        deviceId: Int,
    ): IdentityKeyPair {
        val identity = IdentityKeyPair.generate()
        val bundle = mockk<PreKeyBundle> { every { identityKey } returns identity.publicKey }
        coEvery { keyManager.fetchPreKeyBundle(userId, deviceId) } returns bundle
        return identity
    }

    @Test
    fun `a flood of questions about one person spends one of their pre-keys, not one each`() =
        runTest {
            val alice = publishing("alice", 1)

            repeat(50) { assertEquals(alice.publicKey, manager.publishedIdentityKey("alice", 1)) }

            coVerify(exactly = 1) { keyManager.fetchPreKeyBundle("alice", 1) }
        }

    @Test
    fun `each person and device is asked about separately`() =
        runTest {
            publishing("alice", 1)
            publishing("alice", 2)
            publishing("bob", 1)

            manager.publishedIdentityKey("alice", 1)
            manager.publishedIdentityKey("alice", 2)
            manager.publishedIdentityKey("bob", 1)

            coVerify(exactly = 1) { keyManager.fetchPreKeyBundle("alice", 1) }
            coVerify(exactly = 1) { keyManager.fetchPreKeyBundle("alice", 2) }
            coVerify(exactly = 1) { keyManager.fetchPreKeyBundle("bob", 1) }
        }

    @Test
    fun `an answer is not kept for long, so a changed key is noticed`() =
        runTest {
            publishing("alice", 1)
            manager.publishedIdentityKey("alice", 1)

            ShadowSystemClock.advanceBy(Duration.ofMinutes(6))
            val reinstalled = publishing("alice", 1)

            assertEquals(reinstalled.publicKey, manager.publishedIdentityKey("alice", 1))
        }
}
