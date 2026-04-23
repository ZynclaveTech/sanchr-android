package com.sanchr.core.crypto.store

import org.signal.libsignal.protocol.SignalProtocolAddress

/**
 * Stable on-disk key for a [SignalProtocolAddress]. Must round-trip exactly
 * because it is used as the primary key for the `signal_sessions` and
 * `signal_identities` Room tables. We use `"$name.$deviceId"`, parsing from
 * the **last** `.` so that user IDs containing dots (e.g. UUIDs or emails)
 * parse correctly.
 */
internal fun SignalProtocolAddress.toRoomKey(): String = "$name.$deviceId"

internal fun String.parseSignalAddress(): SignalProtocolAddress {
    val idx = lastIndexOf('.')
    require(idx > 0 && idx < length - 1) { "invalid signal address key: $this" }
    val name = substring(0, idx)
    val deviceId =
        substring(idx + 1).toIntOrNull()
            ?: throw IllegalArgumentException("invalid device id in key: $this")
    return SignalProtocolAddress(name, deviceId)
}
