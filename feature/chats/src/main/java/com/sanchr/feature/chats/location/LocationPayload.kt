package com.sanchr.feature.chats.location

import org.json.JSONObject

/**
 * The body of a `location` message.
 *
 * iOS `MessageSender.sendLocation` writes `{"latitude": <Double>,
 * "longitude": <Double>}` with content type `location`, so this is the exact
 * shape both apps must read and write for a pin sent on one to appear on the
 * other.
 */
object LocationPayload {
    /** The JSON body for a pin at [latitude], [longitude]. */
    fun encode(
        latitude: Double,
        longitude: Double,
    ): String =
        JSONObject()
            .put("latitude", latitude)
            .put("longitude", longitude)
            .toString()

    /**
     * The pin in [body], or null when it is not a location payload.
     *
     * Coordinates outside their valid ranges are rejected rather than
     * clamped: a bad pin should render as an unavailable location, not as a
     * confident marker somewhere in the ocean.
     */
    fun decode(body: String?): Pin? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(body)
            val lat = json.getDouble("latitude")
            val lon = json.getDouble("longitude")
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            if (lat.isNaN() || lon.isNaN()) return null
            Pin(lat, lon)
        }.getOrNull()
    }

    /**
     * A `geo:` URI for [pin], with the coordinates repeated as a query so
     * map apps drop a marker rather than only centring the view.
     */
    fun geoUri(pin: Pin): String = "geo:${pin.latitude},${pin.longitude}?q=${pin.latitude},${pin.longitude}"

    data class Pin(
        val latitude: Double,
        val longitude: Double,
    )
}
