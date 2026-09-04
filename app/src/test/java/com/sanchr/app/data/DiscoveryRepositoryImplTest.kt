package com.sanchr.app.data

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.oprf.Oprf
import com.sanchr.core.crypto.oprf.OprfBlinding
import com.sanchr.core.crypto.oprf.OprfException
import com.sanchr.proto.discovery.DiscoveryServiceClient
import com.sanchr.proto.discovery.GetRegisteredSetResponse
import com.sanchr.proto.discovery.OprfDiscoverRequest
import com.sanchr.proto.discovery.OprfDiscoverResponse
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

/**
 * The protocol's structure — batching, index pairing, the epoch check, the
 * count check, fail-closed — tested with a deterministic stand-in for the
 * group arithmetic. The real arithmetic is proven separately against the
 * golden vectors; here the fakes only need to be consistent with each other.
 */
class DiscoveryRepositoryImplTest {
    private fun sha(vararg parts: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").also { d -> parts.forEach { d.update(it) } }.digest()

    /** blind = H(phone); unblind = identity. Refuses numbers containing "bad". */
    private class FakeOprf : Oprf {
        override fun blind(phoneE164: String): OprfBlinding {
            if ("bad" in phoneE164) throw OprfException("cannot blind")
            val h = MessageDigest.getInstance("SHA-256").digest(phoneE164.toByteArray())
            return OprfBlinding(scalar = ByteArray(32), blindedPoint = h)
        }

        override fun unblind(
            scalar: ByteArray,
            evaluated: ByteArray,
        ): ByteArray = evaluated
    }

    /** evaluate(p) = H("k" || p); registered set = evaluate(H(phone)) for registered phones. */
    private inner class FakeServer(
        registered: List<String>,
        var setEpoch: Long = 7,
        var evalEpoch: Long = 7,
    ) : DiscoveryServiceClient {
        private val k = "k".toByteArray()
        val discoverCalls = mutableListOf<Int>()
        var setFetches = 0
        var setEpochAfterFirstFetch: Long? = null
        private val elements =
            registered.map { sha(k, MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }

        override suspend fun oprfDiscover(request: OprfDiscoverRequest): OprfDiscoverResponse {
            discoverCalls += request.blindedPoints.size
            return OprfDiscoverResponse(request.blindedPoints.map { sha(k, it) }, evalEpoch)
        }

        override suspend fun getRegisteredSet(): GetRegisteredSetResponse {
            setFetches++
            if (setFetches > 1) setEpochAfterFirstFetch?.let { setEpoch = it }
            return GetRegisteredSetResponse(elements, setEpoch)
        }
    }

    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }

    private fun repo(server: FakeServer) = DiscoveryRepositoryImpl(server, FakeOprf(), dispatchers)

    @Test
    fun `returns exactly the registered subset, as the original strings`() =
        runTest {
            val server = FakeServer(registered = listOf("+14155551234", "+919876543210"))
            val result = repo(server).discoverRegistered(listOf("+14155551234", "+442071234567", "+919876543210"))
            assertEquals(setOf("+14155551234", "+919876543210"), result.toSet())
        }

    @Test
    fun `a number that cannot be blinded is skipped without shifting the others`() =
        runTest {
            // If results were paired by position, the skip would make
            // "+442071234567" inherit "+919876543210"'s evaluation and be
            // reported as registered.
            val server = FakeServer(registered = listOf("+919876543210"))
            val result = repo(server).discoverRegistered(listOf("+14155551234", "bad-number", "+442071234567", "+919876543210"))
            assertEquals(listOf("+919876543210"), result)
        }

    @Test
    fun `batches at 500 and maps results across batch boundaries`() =
        runTest {
            val phones = (1..1001).map { "+1415555%04d".format(it) }
            val registered = listOf(phones[0], phones[499], phones[500], phones[1000]) // one per boundary
            val server = FakeServer(registered = registered)

            val result = repo(server).discoverRegistered(phones)

            assertEquals(listOf(500, 500, 1), server.discoverCalls)
            assertEquals(registered.toSet(), result.toSet())
        }

    @Test
    fun `epoch mismatch refetches the registered set once and succeeds`() =
        runTest {
            val server = FakeServer(registered = listOf("+14155551234"), setEpoch = 6, evalEpoch = 7)
            server.setEpochAfterFirstFetch = 7 // the rotation settles by the refetch

            val result = repo(server).discoverRegistered(listOf("+14155551234", "+442071234567"))

            assertEquals(2, server.setFetches)
            assertEquals(listOf("+14155551234"), result)
        }

    @Test
    fun `persistent epoch mismatch fails rather than returning an empty intersection`() =
        runTest {
            val server = FakeServer(registered = listOf("+14155551234"), setEpoch = 6, evalEpoch = 7)
            assertFailsWith<DiscoveryProtocolException> { repo(server).discoverRegistered(listOf("+14155551234")) }
            assertEquals(2, server.setFetches)
        }

    @Test
    fun `a response with the wrong number of points is an error`() =
        runTest {
            val truncating =
                object : DiscoveryServiceClient {
                    override suspend fun oprfDiscover(request: OprfDiscoverRequest) = OprfDiscoverResponse(request.blindedPoints.drop(1), 7)

                    override suspend fun getRegisteredSet() = GetRegisteredSetResponse(emptyList(), 7)
                }
            assertFailsWith<DiscoveryProtocolException> {
                DiscoveryRepositoryImpl(truncating, FakeOprf(), dispatchers).discoverRegistered(listOf("+1", "+2"))
            }
        }

    @Test
    fun `a transport failure propagates - there is no fallback`() =
        runTest {
            val down =
                object : DiscoveryServiceClient {
                    override suspend fun oprfDiscover(request: OprfDiscoverRequest): OprfDiscoverResponse = error("UNAVAILABLE")

                    override suspend fun getRegisteredSet(): GetRegisteredSetResponse = error("UNAVAILABLE")
                }
            assertFailsWith<IllegalStateException> {
                DiscoveryRepositoryImpl(down, FakeOprf(), dispatchers).discoverRegistered(listOf("+14155551234"))
            }
        }

    @Test
    fun `empty input makes no network calls`() =
        runTest {
            val server = FakeServer(registered = listOf("+14155551234"))
            assertTrue(repo(server).discoverRegistered(emptyList()).isEmpty())
            assertEquals(0, server.setFetches)
            assertTrue(server.discoverCalls.isEmpty())
        }

    @Test
    fun `duplicate inputs are queried once`() =
        runTest {
            val server = FakeServer(registered = listOf("+14155551234"))
            val result = repo(server).discoverRegistered(listOf("+14155551234", "+14155551234"))
            assertEquals(listOf(1), server.discoverCalls)
            assertEquals(listOf("+14155551234"), result)
        }
}
