package com.sanchr.domain.messaging

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class PresenceStatus {
    ONLINE,
    OFFLINE,

    /** The peer has online status turned off; show nothing. */
    HIDDEN,
}

class PeerPresence(
    val status: PresenceStatus,
    /** Unix millis the peer reported, or null when online or unknown. */
    val lastSeenMs: Long?,
    /** When this device received the update; a stale ONLINE decays to unknown. */
    val receivedAtMs: Long,
)

/**
 * What peers have told us about themselves over sealed `presence/v1`
 * payloads (see `SendPresenceUseCase`). In memory only: presence is a
 * live signal, not a record, and the server never sees it.
 */
@Singleton
class PresenceStore(
    /** Injectable clock so tests can age entries; production uses wall time. */
    private val nowMillis: () -> Long,
) {
    @Inject
    constructor() : this(System::currentTimeMillis)

    private val _presence = MutableStateFlow<Map<String, PeerPresence>>(emptyMap())
    val presence: StateFlow<Map<String, PeerPresence>> = _presence

    fun update(
        userId: String,
        status: PresenceStatus,
        lastSeenMs: Long?,
    ) {
        _presence.update { it + (userId to PeerPresence(status, lastSeenMs?.takeIf { ms -> ms > 0 }, nowMillis())) }
    }

    /**
     * The status line for [userId]: "Online", "Last seen …", or null when
     * there is nothing to show (hidden, never heard from, or an ONLINE that
     * is older than the peer's 30-second broadcast interval allows).
     */
    fun statusLine(
        userId: String,
        now: Long = nowMillis(),
    ): String? {
        val p = _presence.value[userId] ?: return null
        return when (p.status) {
            PresenceStatus.HIDDEN -> null
            PresenceStatus.ONLINE -> if (now - p.receivedAtMs <= ONLINE_TTL_MS) "Online" else null
            PresenceStatus.OFFLINE -> p.lastSeenMs?.let { "Last seen ${relative(now - it)}" }
        }
    }

    fun clear() {
        _presence.value = emptyMap()
    }

    companion object {
        /** iOS broadcasts ONLINE every 30 s; two missed beats and we stop claiming it. */
        const val ONLINE_TTL_MS = 75_000L
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR

        internal fun relative(agoMs: Long): String =
            when {
                agoMs < MINUTE -> "just now"
                agoMs < HOUR -> "${agoMs / MINUTE} min ago"
                agoMs < DAY -> "${agoMs / HOUR} h ago"
                else -> "${agoMs / DAY} d ago"
            }
    }
}
