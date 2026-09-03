package com.sanchr.domain.messaging

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.messaging.DeliveryTokenRequest
import com.sanchr.proto.messaging.MessagingServiceClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A persisted pool of anonymous delivery tokens, spent one per sealed send.
 *
 * A sealed send carries no JWT — the delivery token is its only credential
 * — so this pool has to be right rather than merely fast: a token handed to
 * two callers means one of those sends is rejected by the server with
 * `UNAUTHENTICATED` ("invalid or expired delivery token"), while a token
 * lost to a process death is merely wasted. [acquire] is written to that
 * asymmetry — it pops and persists a token atomically, before returning it.
 *
 * The in-memory pool is guarded by [poolLock], a plain intrinsic lock
 * rather than a suspending [Mutex]: every operation performed while holding
 * it (a deque removal/append, and a synchronous `SharedPreferences` write
 * via [SessionManager]) is non-suspending, so a cheap lock is enough and it
 * lets [clear] stay a plain, non-suspend function.
 *
 * The network fetch itself is deliberately kept *outside* [poolLock] and
 * coordinated by a separate [fetchMutex] single-flight (see [fetchOnce]):
 * holding the pool lock across a `GetDeliveryTokens` RPC would serialize
 * every concurrent send behind one network round trip, even sends that
 * only needed to pop an already-available token.
 */
@Singleton
class DeliveryTokenStore
    @Inject
    constructor(
        private val messagingClient: MessagingServiceClient,
        private val sessionManager: SessionManager,
        private val dispatchers: DispatcherProvider,
    ) {
        private val poolLock = Any()
        private val pool: ArrayDeque<ByteArray> = ArrayDeque(sessionManager.getDeliveryTokens())

        private val fetchMutex = Mutex()
        private var inFlightFetch: CompletableDeferred<Unit>? = null

        /**
         * Pops the oldest token, fetching a fresh batch first if the pool is
         * empty. Persists the removal before returning, so a process death
         * immediately after can never resurrect an already-handed-out token.
         *
         * Throws [DeliveryTokenPoolEmptyException] if the pool is still
         * empty after a fetch attempt (the server silently caps a batch at
         * 100 and can, in principle, return zero).
         */
        suspend fun acquire(): ByteArray =
            withContext(dispatchers.io) {
                popOrNull()?.let { return@withContext it }

                fetchOnce()

                popOrNull() ?: throw DeliveryTokenPoolEmptyException()
            }

        /** Tops the pool back up to [REQUEST_COUNT] if it has fallen below [LOW_WATER_MARK]. */
        suspend fun replenishIfNeeded() =
            withContext(dispatchers.io) {
                val needsFetch = synchronized(poolLock) { pool.size < LOW_WATER_MARK }
                if (needsFetch) fetchOnce()
            }

        /** Drops every cached token from memory and disk, e.g. on sign-out. */
        fun clear() {
            synchronized(poolLock) {
                pool.clear()
                persistLocked()
            }
        }

        private fun popOrNull(): ByteArray? =
            synchronized(poolLock) {
                val token = pool.removeFirstOrNull() ?: return@synchronized null
                persistLocked()
                token
            }

        /** Must only be called while holding [poolLock]. */
        private fun persistLocked() {
            sessionManager.saveDeliveryTokens(pool.toList())
        }

        /**
         * Fetches one batch, single-flight: concurrent callers that arrive
         * while a fetch is already in progress await that fetch's result
         * instead of spending a second `GetDeliveryTokens` call — the RPC is
         * rate-limited to 60 per 60 s per user, and a second concurrent
         * fetch buys nothing a single one doesn't already deliver.
         */
        private suspend fun fetchOnce() {
            val (outcome, isOwner) =
                fetchMutex.withLock {
                    val existing = inFlightFetch
                    if (existing != null) {
                        existing to false
                    } else {
                        val created = CompletableDeferred<Unit>()
                        inFlightFetch = created
                        created to true
                    }
                }

            if (!isOwner) {
                outcome.await()
                return
            }

            try {
                val response = messagingClient.getDeliveryTokens(DeliveryTokenRequest(count = REQUEST_COUNT))
                when {
                    response.tokens.isEmpty() -> Log.w(TAG, "GetDeliveryTokens returned zero tokens")
                    response.tokens.size < REQUEST_COUNT ->
                        Log.w(TAG, "GetDeliveryTokens returned ${response.tokens.size} of $REQUEST_COUNT requested")
                }
                synchronized(poolLock) {
                    pool.addAll(response.tokens)
                    persistLocked()
                }
                outcome.complete(Unit)
            } catch (e: Exception) {
                outcome.completeExceptionally(e)
                throw e
            } finally {
                fetchMutex.withLock { inFlightFetch = null }
            }
        }

        private companion object {
            const val TAG = "DeliveryTokenStore"

            /** Matches iOS's `tokenBatchSize` (`SealedSenderManager.swift`). Server caps at 100 anyway. */
            const val REQUEST_COUNT = 50

            /** Matches iOS's low-water mark. Refill before this many remain. */
            const val LOW_WATER_MARK = 10
        }
    }

/**
 * Thrown by [DeliveryTokenStore.acquire] when the pool is still empty after
 * a fetch attempt — the server returned zero tokens rather than the
 * requested batch.
 */
class DeliveryTokenPoolEmptyException : Exception("Delivery token pool is empty after a fetch attempt")
