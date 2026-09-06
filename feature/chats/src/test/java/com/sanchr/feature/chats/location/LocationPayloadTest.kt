package com.sanchr.feature.chats.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The payload is the contract with iOS, which writes
 * `{"latitude": ..., "longitude": ...}` for content type `location`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class LocationPayloadTest {
    @Test
    fun `a pin round-trips`() {
        val encoded = LocationPayload.encode(12.9716, 77.5946)

        assertEquals(LocationPayload.Pin(12.9716, 77.5946), LocationPayload.decode(encoded))
    }

    @Test
    fun `the encoded shape is the one iOS writes`() {
        val encoded = LocationPayload.encode(1.5, -2.5)

        assertEquals(LocationPayload.Pin(1.5, -2.5), LocationPayload.decode(encoded))
        assert(encoded.contains("\"latitude\"")) { encoded }
        assert(encoded.contains("\"longitude\"")) { encoded }
    }

    @Test
    fun `text that is not a location decodes to nothing`() {
        assertNull(LocationPayload.decode("see you at six"))
        assertNull(LocationPayload.decode("{}"))
        assertNull(LocationPayload.decode(null))
        assertNull(LocationPayload.decode(""))
    }

    @Test
    fun `coordinates outside their range are rejected rather than clamped`() {
        assertNull(LocationPayload.decode("""{"latitude":91.0,"longitude":0.0}"""))
        assertNull(LocationPayload.decode("""{"latitude":0.0,"longitude":181.0}"""))
        assertNull(LocationPayload.decode("""{"latitude":-90.1,"longitude":0.0}"""))
    }

    @Test
    fun `the extremes of the valid range are accepted`() {
        assertEquals(LocationPayload.Pin(-90.0, -180.0), LocationPayload.decode("""{"latitude":-90.0,"longitude":-180.0}"""))
        assertEquals(LocationPayload.Pin(90.0, 180.0), LocationPayload.decode("""{"latitude":90.0,"longitude":180.0}"""))
    }

    @Test
    fun `the geo uri repeats the point as a query so a marker is dropped`() {
        val uri = LocationPayload.geoUri(LocationPayload.Pin(12.9716, 77.5946))

        assertEquals("geo:12.9716,77.5946?q=12.9716,77.5946", uri)
    }
}
