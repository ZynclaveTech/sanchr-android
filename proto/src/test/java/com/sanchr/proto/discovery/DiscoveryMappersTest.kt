package com.sanchr.proto.discovery

import com.google.protobuf.ByteString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import sanchr.discovery.Discovery

/**
 * The discovery wire contract is byte-exact: 32-byte raw points, in order,
 * plus a key epoch. These pin the mapping between the hand-written models and
 * the generated protobuf types, with no channel involved.
 */
class DiscoveryMappersTest {
    private fun point(fill: Int) = ByteArray(32) { fill.toByte() }

    @Test
    fun `request carries every blinded point as raw bytes in order`() {
        val proto = OprfDiscoverRequest(listOf(point(1), point(2), point(3))).toProto()

        assertEquals(3, proto.blindedPointsCount)
        assertContentEquals(point(1), proto.getBlindedPoints(0).toByteArray())
        assertContentEquals(point(3), proto.getBlindedPoints(2).toByteArray())
    }

    @Test
    fun `discover response preserves order and carries the key epoch`() {
        val proto =
            Discovery.OprfDiscoverResponse
                .newBuilder()
                .addEvaluatedPoints(ByteString.copyFrom(point(9)))
                .addEvaluatedPoints(ByteString.copyFrom(point(8)))
                .setKeyEpoch(42)
                .build()

        val model = proto.toModel()

        assertEquals(2, model.evaluatedPoints.size)
        assertContentEquals(point(9), model.evaluatedPoints[0])
        assertContentEquals(point(8), model.evaluatedPoints[1])
        assertEquals(42L, model.keyEpoch)
    }

    @Test
    fun `registered set response carries elements and the key epoch`() {
        val proto =
            Discovery.GetRegisteredSetResponse
                .newBuilder()
                .addSetElements(ByteString.copyFrom(point(5)))
                .setKeyEpoch(7)
                .build()

        val model = proto.toModel()

        assertEquals(1, model.setElements.size)
        assertContentEquals(point(5), model.setElements[0])
        assertEquals(7L, model.keyEpoch)
    }

    @Test
    fun `a server that predates key_epoch reads as epoch zero, not a crash`() {
        // proto3 default for an absent uint64 field.
        assertEquals(
            0L,
            Discovery.OprfDiscoverResponse
                .getDefaultInstance()
                .toModel()
                .keyEpoch,
        )
    }
}
