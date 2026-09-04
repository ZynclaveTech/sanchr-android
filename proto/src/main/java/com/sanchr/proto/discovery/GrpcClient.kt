package com.sanchr.proto.discovery

import com.google.protobuf.ByteString
import io.grpc.CallOptions
import io.grpc.Channel
import sanchr.discovery.Discovery
import sanchr.discovery.DiscoveryServiceGrpcKt

/**
 * gRPC client interface for the DiscoveryService — privacy-preserving contact
 * discovery via a 2-HashDH OPRF over ristretto255. The server learns which
 * blinded points it evaluated, never which phone numbers they came from.
 *
 * `GetBloomFilter` is deliberately not exposed: iOS declares it and never
 * calls it in production, and the registered-set path is the one both
 * clients actually use.
 */
interface DiscoveryServiceClient {
    /**
     * Evaluates a batch of blinded points. The server rejects more than
     * [OprfDiscoverRequest.MAX_BATCH_SIZE] points and anything not exactly
     * 32 bytes, fails the whole batch on a single invalid point, and rate
     * limits to 20 calls per user per hour.
     */
    suspend fun oprfDiscover(request: OprfDiscoverRequest): OprfDiscoverResponse

    /**
     * The full registered set under the current key. Unpaginated; the channel
     * allows 16 MiB inbound, roughly 450k users at ~35 bytes each. Rate limited
     * to 6 calls per user per hour.
     */
    suspend fun getRegisteredSet(): GetRegisteredSetResponse
}

class DiscoveryServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : DiscoveryServiceClient {
    private val stub by lazy { DiscoveryServiceGrpcKt.DiscoveryServiceCoroutineStub(channel, callOptions) }

    override suspend fun oprfDiscover(request: OprfDiscoverRequest): OprfDiscoverResponse = stub.oprfDiscover(request.toProto()).toModel()

    override suspend fun getRegisteredSet(): GetRegisteredSetResponse =
        stub.getRegisteredSet(Discovery.GetRegisteredSetRequest.getDefaultInstance()).toModel()
}

// region ── Mappers ─────────────────────────────────────────────────────────
// Internal rather than private so the mapping is unit-testable without a
// channel; the byte-level contract is the part worth pinning.

internal fun OprfDiscoverRequest.toProto(): Discovery.OprfDiscoverRequest =
    Discovery.OprfDiscoverRequest
        .newBuilder()
        .addAllBlindedPoints(blindedPoints.map { ByteString.copyFrom(it) })
        .build()

internal fun Discovery.OprfDiscoverResponse.toModel(): OprfDiscoverResponse =
    OprfDiscoverResponse(
        evaluatedPoints = evaluatedPointsList.map { it.toByteArray() },
        keyEpoch = keyEpoch,
    )

internal fun Discovery.GetRegisteredSetResponse.toModel(): GetRegisteredSetResponse =
    GetRegisteredSetResponse(
        setElements = setElementsList.map { it.toByteArray() },
        keyEpoch = keyEpoch,
    )

// endregion
