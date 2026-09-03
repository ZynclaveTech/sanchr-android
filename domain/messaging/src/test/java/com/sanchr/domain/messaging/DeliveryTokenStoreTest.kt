package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.messaging.DeliveryTokenRequest
import com.sanchr.proto.messaging.DeliveryTokenResponse
import com.sanchr.proto.messaging.MessagingServiceClient
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

class DeliveryTokenStoreTest {
    private val messagingClient = mockk<MessagingServiceClient>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }

    private fun token(byte: Int): ByteArray = ByteArray(32) { byte.toByte() }

    private fun newStore(initialTokens: List<ByteArray> = emptyList()): DeliveryTokenStore {
        every { sessionManager.getDeliveryTokens() } returns initialTokens
        return DeliveryTokenStore(messagingClient, sessionManager, dispatchers)
    }

    @Test
    fun `acquire hands out tokens in insertion order`() =
        runTest {
            val store = newStore(listOf(token(1), token(2), token(3)))

            assertContentEquals(token(1), store.acquire())
            assertContentEquals(token(2), store.acquire())
            assertContentEquals(token(3), store.acquire())

            coVerify(exactly = 0) { messagingClient.getDeliveryTokens(any()) }
        }

    @Test
    fun `acquire on an empty pool fetches exactly once and returns a token`() =
        runTest {
            val store = newStore(emptyList())
            coEvery { messagingClient.getDeliveryTokens(DeliveryTokenRequest(count = 50)) } returns
                DeliveryTokenResponse(tokens = listOf(token(1), token(2)))

            val acquired = store.acquire()

            assertContentEquals(token(1), acquired)
            coVerify(exactly = 1) { messagingClient.getDeliveryTokens(any()) }
        }

    @Test
    fun `concurrent acquire on an empty pool produces exactly one fetch`() =
        runTest {
            // A dedicated DispatcherProvider for this test only, bound to
            // runTest's own scheduler via a queueing StandardTestDispatcher
            // rather than the class-level Dispatchers.Unconfined above.
            //
            // Dispatchers.Unconfined does not queue: nested `withContext`
            // onto it resumes inline through a thread-local event loop, so
            // a coroutine that suspends inside it (e.g. at `yield()` below)
            // drains straight back to completion without ever handing
            // control to a sibling `async` still waiting to start. Verified
            // empirically — with the class-level Unconfined dispatchers,
            // this test passed even after the single-flight guard was
            // deleted, because the two acquire() calls never actually
            // overlapped (first finished entirely before second began).
            // StandardTestDispatcher shares one FIFO queue across both
            // `async` bodies, so `yield()` genuinely hands off between them.
            val schedulerBoundDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler)
            val concurrencyTestDispatchers =
                object : DispatcherProvider {
                    override val main: CoroutineDispatcher = schedulerBoundDispatcher
                    override val io: CoroutineDispatcher = schedulerBoundDispatcher
                    override val default: CoroutineDispatcher = schedulerBoundDispatcher
                    override val unconfined: CoroutineDispatcher = schedulerBoundDispatcher
                    override val signalDispatcher: CoroutineDispatcher = schedulerBoundDispatcher
                }
            every { sessionManager.getDeliveryTokens() } returns emptyList()
            val store = DeliveryTokenStore(messagingClient, sessionManager, concurrencyTestDispatchers)
            coEvery { messagingClient.getDeliveryTokens(any()) } coAnswers {
                // A real suspension point: without it, the mocked call would
                // resolve synchronously and the two acquire() calls below
                // would never genuinely overlap, making the single-flight
                // assertion pass trivially even without a guard.
                yield()
                DeliveryTokenResponse(tokens = listOf(token(1), token(2)))
            }

            val first = async { store.acquire() }
            val second = async { store.acquire() }
            val results = awaitAll(first, second)

            coVerify(exactly = 1) { messagingClient.getDeliveryTokens(any()) }
            assertFalse(results[0].contentEquals(results[1]), "the same token must not be handed to two callers")
        }

    @Test
    fun `a fetch that returns nothing throws DeliveryTokenPoolEmptyException`() =
        runTest {
            val store = newStore(emptyList())
            coEvery { messagingClient.getDeliveryTokens(any()) } returns DeliveryTokenResponse(tokens = emptyList())

            assertFailsWith<DeliveryTokenPoolEmptyException> {
                store.acquire()
            }
        }

    @Test
    fun `acquire removes the token from the persisted pool before returning`() =
        runTest {
            val persisted = mutableListOf<List<ByteArray>>()
            every { sessionManager.getDeliveryTokens() } returns listOf(token(1), token(2))
            every { sessionManager.saveDeliveryTokens(capture(persisted)) } just Runs
            val store = DeliveryTokenStore(messagingClient, sessionManager, dispatchers)

            store.acquire()

            val afterAcquire = persisted.last()
            assertTrue(afterAcquire.none { it.contentEquals(token(1)) }, "acquired token must be removed from the persisted pool")
            assertTrue(afterAcquire.any { it.contentEquals(token(2)) })
        }

    @Test
    fun `pool survives via SessionManager across a new instance`() =
        runTest {
            every { sessionManager.getDeliveryTokens() } returns listOf(token(9))
            val store = DeliveryTokenStore(messagingClient, sessionManager, dispatchers)

            val acquired = store.acquire()

            assertContentEquals(token(9), acquired)
            coVerify(exactly = 0) { messagingClient.getDeliveryTokens(any()) }
        }

    @Test
    fun `clear empties the pool and persists the removal`() =
        runTest {
            val store = newStore(listOf(token(1), token(2)))
            coEvery { messagingClient.getDeliveryTokens(any()) } returns DeliveryTokenResponse(tokens = emptyList())

            store.clear()

            verify { sessionManager.saveDeliveryTokens(emptyList()) }
            assertFailsWith<DeliveryTokenPoolEmptyException> { store.acquire() }
        }

    @Test
    fun `replenishIfNeeded fetches when below the low-water mark but not when above it`() =
        runTest {
            val store = newStore(List(9) { token(it) })
            coEvery { messagingClient.getDeliveryTokens(any()) } returns
                DeliveryTokenResponse(tokens = listOf(token(50)))

            store.replenishIfNeeded()
            coVerify(exactly = 1) { messagingClient.getDeliveryTokens(any()) }

            // Now at 9 + 1 fetched = 10, at the low-water mark but not below it.
            store.replenishIfNeeded()
            coVerify(exactly = 1) { messagingClient.getDeliveryTokens(any()) }
        }
}
