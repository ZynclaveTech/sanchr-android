package com.sanchr.domain.messaging

/**
 * Decides whether a session with a sender's device should be rebuilt now.
 *
 * A message that cannot be decrypted for want of a session is answered by
 * starting a new one with its sender, so that the next thing sent to them
 * tells them to start over too. One rebuild does that. But the rebuild was
 * launched for every such message, with nothing to say one was already under
 * way or had just been done:
 *
 *  - Ten undecryptable messages arriving together launched ten, each fetching
 *    a bundle and so each taking one of the sender's one-time pre-keys, and
 *    each replacing the session the one before it had just made.
 *  - The message is left on the server to be delivered again, so the same
 *    thing happened on every sync for as long as it stayed undecryptable.
 *
 * A contact with one stuck message could have their one-time pre-keys drained
 * by the device they were talking to. This allows one rebuild per device at a
 * time, and not again until [cooldownMillis] has passed.
 *
 * Held in memory. A process that is restarted forgets, so the bound is per
 * process lifetime rather than absolute; the server's own limit on bundles
 * per target is what holds beyond that.
 */
class SessionRebuildThrottle(
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
    private val now: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {
    private val lastStarted = HashMap<String, Long>()
    private val inFlight = HashSet<String>()

    /**
     * Claims the rebuild for this device if one is due. A caller given true
     * must call [finished] when its attempt ends, however it ends.
     */
    @Synchronized
    fun tryStart(
        userId: String,
        deviceId: Int,
    ): Boolean {
        val key = "$userId.$deviceId"
        if (key in inFlight) return false
        val last = lastStarted[key]
        if (last != null && now() - last < cooldownMillis) return false
        inFlight += key
        lastStarted[key] = now()
        return true
    }

    @Synchronized
    fun finished(
        userId: String,
        deviceId: Int,
    ) {
        inFlight -= "$userId.$deviceId"
    }

    private companion object {
        const val DEFAULT_COOLDOWN_MILLIS = 10 * 60 * 1000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
