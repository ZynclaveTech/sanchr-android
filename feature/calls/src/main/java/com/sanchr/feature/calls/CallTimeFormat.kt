package com.sanchr.feature.calls

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * When a call in the history happened, as the list shows it.
 *
 * The server sends call times in **seconds**. The list passed them straight
 * to a formatter that took milliseconds, so every call was dated a few weeks
 * into January 1970 — shown as "Jan 21" or thereabouts on every row, whenever
 * the call actually was.
 */
internal object CallTimeFormat {
    private const val MILLIS_PER_SECOND = 1_000L
    private const val DAY_MILLIS = 24 * 60 * 60 * MILLIS_PER_SECOND
    private const val WEEK_MILLIS = 7 * DAY_MILLIS

    /**
     * The time for a call in the last day, the weekday for one in the last
     * week, and the date before that. Empty when the server did not say.
     */
    fun listLabel(
        startedAtSeconds: Long,
        nowMillis: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault(),
        zone: TimeZone = TimeZone.getDefault(),
    ): String {
        if (startedAtSeconds <= 0L) return ""
        val startedAtMillis = startedAtSeconds * MILLIS_PER_SECOND
        val age = nowMillis - startedAtMillis
        val pattern =
            when {
                age < DAY_MILLIS -> "h:mm a"
                age < WEEK_MILLIS -> "EEE"
                else -> "MMM d"
            }
        return SimpleDateFormat(pattern, locale).apply { timeZone = zone }.format(Date(startedAtMillis))
    }
}
