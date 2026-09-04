package com.sanchr.proto.discovery

/**
 * Hand-written wire models for `sanchr.discovery.DiscoveryService`, following
 * the per-service pattern used by every other client in this module.
 *
 * All points are raw 32-byte compressed ristretto255 encodings, exactly as
 * `discovery.proto` declares them (`repeated bytes`) — never hex, never
 * base64. [keyEpoch] is the rotation epoch of the server secret that produced
 * the response; see [OprfDiscoverResponse.keyEpoch].
 */
class OprfDiscoverRequest(
    /** Blinded points `r · H(phone)`, each 32 bytes; at most [MAX_BATCH_SIZE]. */
    val blindedPoints: List<ByteArray>,
) {
    companion object {
        /** Server-enforced cap (`MAX_OPRF_BATCH_SIZE`); larger batches are rejected outright. */
        const val MAX_BATCH_SIZE = 500
    }
}

class OprfDiscoverResponse(
    /** `k · blindedPoint`, in request order, same count as the request. */
    val evaluatedPoints: List<ByteArray>,
    /**
     * Rotation epoch of the server secret that evaluated these points. Must
     * equal [GetRegisteredSetResponse.keyEpoch] for the two to be comparable:
     * if they differ, the values were produced under different keys and will
     * not intersect — refetch rather than concluding nobody is registered.
     */
    val keyEpoch: Long,
)

class GetRegisteredSetResponse(
    /** `k · H(phone)` for every registered, verified, active user; 32 bytes each. */
    val setElements: List<ByteArray>,
    /** See [OprfDiscoverResponse.keyEpoch]. */
    val keyEpoch: Long,
)
