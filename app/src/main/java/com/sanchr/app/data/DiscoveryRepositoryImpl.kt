package com.sanchr.app.data

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.oprf.Oprf
import com.sanchr.core.crypto.oprf.OprfException
import com.sanchr.domain.contacts.DiscoveryRepository
import com.sanchr.proto.discovery.DiscoveryServiceClient
import com.sanchr.proto.discovery.GetRegisteredSetResponse
import com.sanchr.proto.discovery.OprfDiscoverRequest
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * OPRF contact discovery. Mirrors iOS's `DiscoveryRepository`, including the
 * two properties its tests guard:
 *
 * - **Results are paired by original index, never by position.** The native
 *   layer can fail to blind a number; if it does, the batch is shorter than
 *   the input and positional mapping would name the *wrong contact* as
 *   registered. Each blinding carries the index it came from.
 * - **The registered set and the evaluations must be under the same key.**
 *   Every replica derives the same key per epoch, so a mismatch only arises
 *   when a client straddles a rotation boundary. The set is refetched once;
 *   if the epochs still disagree, this throws rather than returning an empty
 *   intersection that reads as "nobody you know is registered."
 */
class DiscoveryRepositoryImpl
    @Inject
    constructor(
        private val discoveryClient: DiscoveryServiceClient,
        private val oprf: Oprf,
        private val dispatchers: DispatcherProvider,
    ) : DiscoveryRepository {
        private class Blinding(
            val originalIndex: Int,
            val scalar: ByteArray,
            val blindedPoint: ByteArray,
        )

        override suspend fun discoverRegistered(phoneNumbersE164: List<String>): List<String> =
            withContext(dispatchers.default) {
                val phones = phoneNumbersE164.distinct()
                if (phones.isEmpty()) return@withContext emptyList()

                val blindings =
                    phones.mapIndexedNotNull { index, phone ->
                        try {
                            val b = oprf.blind(phone)
                            Blinding(index, b.scalar, b.blindedPoint)
                        } catch (e: OprfException) {
                            // Skipped, not misattributed: see the index pairing above.
                            Log.w(TAG, "could not blind a number; excluding it from discovery", e)
                            null
                        }
                    }
                if (blindings.isEmpty()) return@withContext emptyList()

                // Fetch the set first so the two calls are as close together as
                // the batching allows, then evaluate.
                var registered = discoveryClient.getRegisteredSet()
                val unblindedByIndex = HashMap<Int, ByteArray>(blindings.size)
                var evaluatedEpoch: Long? = null

                for (batch in blindings.chunked(OprfDiscoverRequest.MAX_BATCH_SIZE)) {
                    val response = discoveryClient.oprfDiscover(OprfDiscoverRequest(batch.map { it.blindedPoint }))
                    if (response.evaluatedPoints.size != batch.size) {
                        throw DiscoveryProtocolException(
                            "OPRF returned ${response.evaluatedPoints.size} points for ${batch.size} queries",
                        )
                    }
                    if (evaluatedEpoch != null && response.keyEpoch != evaluatedEpoch) {
                        // The key rotated between our own batches; nothing sensible
                        // can be assembled from a split run.
                        throw DiscoveryProtocolException("server key rotated mid-discovery (epochs $evaluatedEpoch, ${response.keyEpoch})")
                    }
                    evaluatedEpoch = response.keyEpoch
                    batch.forEachIndexed { i, blinding ->
                        unblindedByIndex[blinding.originalIndex] = oprf.unblind(blinding.scalar, response.evaluatedPoints[i])
                    }
                }

                registered = reconcileEpoch(registered, requireNotNull(evaluatedEpoch))

                val registeredSet = registered.setElements.mapTo(HashSet(registered.setElements.size)) { Key(it) }
                unblindedByIndex.entries
                    .filter { Key(it.value) in registeredSet }
                    .map { phones[it.key] }
            }

        /**
         * Returns a registered set whose epoch matches [evaluatedEpoch],
         * refetching once if needed. A second mismatch is an error, not an
         * empty result.
         */
        private suspend fun reconcileEpoch(
            current: GetRegisteredSetResponse,
            evaluatedEpoch: Long,
        ): GetRegisteredSetResponse {
            if (current.keyEpoch == evaluatedEpoch) return current
            Log.i(TAG, "registered set is epoch ${current.keyEpoch} but evaluations are $evaluatedEpoch; refetching")
            val refreshed = discoveryClient.getRegisteredSet()
            if (refreshed.keyEpoch != evaluatedEpoch) {
                throw DiscoveryProtocolException(
                    "registered set (epoch ${refreshed.keyEpoch}) and evaluations (epoch $evaluatedEpoch) are under different keys",
                )
            }
            return refreshed
        }

        /** ByteArray with value equality, for set membership. */
        private class Key(
            val bytes: ByteArray,
        ) {
            override fun equals(other: Any?): Boolean = other is Key && bytes.contentEquals(other.bytes)

            override fun hashCode(): Int = bytes.contentHashCode()
        }

        private companion object {
            const val TAG = "DiscoveryRepository"
        }
    }

/** The server's responses could not be assembled into a trustworthy result. */
class DiscoveryProtocolException(
    message: String,
) : Exception(message)
