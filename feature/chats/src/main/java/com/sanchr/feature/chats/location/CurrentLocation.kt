package com.sanchr.feature.chats.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * The device's last known position, using the platform LocationManager.
 *
 * Deliberately not Play Services: a "share my location" bubble does not
 * justify another dependency, and the platform API works on devices without
 * Google services.
 *
 * Last known rather than a live fix, because a fix can take many seconds
 * and the user tapped a button expecting a message to send.
 */
object CurrentLocation {
    /**
     * The best recent fix, or null when permission is missing, location is
     * off, or nothing has a fix yet.
     *
     * Providers are tried newest-fix-first rather than in a fixed order, so
     * a stale GPS fix does not beat a fresh network one.
     */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): LocationPayload.Pin? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val best =
            runCatching {
                manager
                    .getProviders(true)
                    .mapNotNull { provider ->
                        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                    }.maxByOrNull { it.time }
            }.getOrNull() ?: return null
        return LocationPayload.Pin(best.latitude, best.longitude)
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
