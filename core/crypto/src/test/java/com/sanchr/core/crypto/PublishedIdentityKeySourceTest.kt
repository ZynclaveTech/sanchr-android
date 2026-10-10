package com.sanchr.core.crypto

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.proto.keys.IdentityKeyLookupUnsupported
import com.sanchr.proto.keys.KeyServiceClient
import com.sanchr.proto.keys.KyberPreKey
import com.sanchr.proto.keys.PreKeyBundleResponse
import com.sanchr.proto.keys.SignedPreKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType

/**
 * Where the answer to "which key does the server publish for this person?"
 * comes from. The call made for it costs that person nothing; a pre-key
 * bundle spends one of their one-time pre-keys, and is asked for only when
 * the server has no other way to answer.
 */
class PublishedIdentityKeySourceTest {
    private val client = mockk<KeyServiceClient>()
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }
    private val keyManager =
        SignalKeyManager(
            store = mockk(relaxed = true),
            identityKeyStore = mockk(relaxed = true),
            preKeyStore = mockk(relaxed = true),
            signedPreKeyStore = mockk(relaxed = true),
            kyberPreKeyStore = mockk(relaxed = true),
            sessionManager = mockk(relaxed = true),
            keyServiceClient = client,
            dispatchers = dispatchers,
        )
    private val alice = IdentityKeyPair.generate()

    private fun bundleOf(identity: IdentityKeyPair): PreKeyBundleResponse {
        val signed = ECKeyPair.generate()
        val kyber = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        return PreKeyBundleResponse(
            identityPublicKey = identity.publicKey.serialize(),
            signedPreKey =
                SignedPreKey(
                    keyId = 1,
                    publicKey = signed.publicKey.serialize(),
                    signature = identity.privateKey.calculateSignature(signed.publicKey.serialize()),
                ),
            registrationId = 7,
            deviceId = 1,
            kyberPreKey =
                KyberPreKey(
                    keyId = 2,
                    publicKey = kyber.publicKey.serialize(),
                    signature = identity.privateKey.calculateSignature(kyber.publicKey.serialize()),
                ),
        )
    }

    @Test
    fun `the key comes from the call made for it, and no bundle is fetched`() =
        runTest {
            coEvery { client.getIdentityKey("alice", 1) } returns alice.publicKey.serialize()

            assertEquals(alice.publicKey, keyManager.fetchPublishedIdentityKey("alice", 1))

            // A bundle would have cost Alice a one-time pre-key.
            coVerify(exactly = 0) { client.getPreKeyBundle(any()) }
        }

    @Test
    fun `a user the server publishes no key for is an answer, and still costs no bundle`() =
        runTest {
            coEvery { client.getIdentityKey("nobody", 1) } returns null

            assertNull(keyManager.fetchPublishedIdentityKey("nobody", 1))

            coVerify(exactly = 0) { client.getPreKeyBundle(any()) }
        }

    @Test
    fun `a server that predates the call is asked for a bundle instead`() =
        runTest {
            coEvery { client.getIdentityKey("alice", 1) } throws IdentityKeyLookupUnsupported()
            coEvery { client.getPreKeyBundle(any()) } returns bundleOf(alice)

            assertEquals(alice.publicKey, keyManager.fetchPublishedIdentityKey("alice", 1))

            coVerify(exactly = 1) { client.getPreKeyBundle(any()) }
        }

    @Test
    fun `a transport failure is not mistaken for an old server`() =
        runTest {
            coEvery { client.getIdentityKey("alice", 1) } throws IOException("offline")

            assertFailsWith<IOException> { keyManager.fetchPublishedIdentityKey("alice", 1) }

            // Falling back here would spend a pre-key every time the network blipped.
            coVerify(exactly = 0) { client.getPreKeyBundle(any()) }
        }
}
