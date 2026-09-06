package com.sanchr.feature.chats.info

/**
 * The disappearing-message timers a conversation can be set to.
 *
 * The same set the account-wide default offers, so a per-chat choice and the
 * global one cannot disagree about what "5m" means.
 */
object DisappearingDurations {
    /** Labels in the order the screen lists them. */
    val LABELS: List<String> = listOf("off", "30s", "5m", "1h", "24h", "7d")

    private const val SECOND = 1_000L
    private const val MINUTE = 60 * SECOND
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    private val MILLIS: Map<String, Long> =
        mapOf(
            "30s" to 30 * SECOND,
            "5m" to 5 * MINUTE,
            "1h" to HOUR,
            "24h" to DAY,
            "7d" to 7 * DAY,
        )

    /**
     * Milliseconds for [label], or null for "off" and for anything this build
     * does not know.
     *
     * Null rather than zero: the send path reads null as "no per-chat timer,
     * use the account default", while zero would read as a timer of no length.
     */
    fun millisOf(label: String): Long? = MILLIS[label]

    /**
     * Inverse of [millisOf]. A stored duration this build has no label for
     * shows as "off" rather than selecting the wrong row, matching how the
     * account-wide setting already degrades.
     */
    fun labelOf(millis: Long?): String {
        if (millis == null || millis <= 0L) return "off"
        return MILLIS.entries.firstOrNull { it.value == millis }?.key ?: "off"
    }

    /** How the label reads on screen. */
    fun displayName(label: String): String =
        when (label) {
            "off" -> "Off"
            "30s" -> "30 seconds"
            "5m" -> "5 minutes"
            "1h" -> "1 hour"
            "24h" -> "24 hours"
            "7d" -> "7 days"
            else -> label
        }
}
