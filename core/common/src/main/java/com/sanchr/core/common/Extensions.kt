package com.sanchr.core.common

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Formats a timestamp (epoch millis) into a human-readable relative time string.
 * Examples: "Just now", "5m ago", "2h ago", "Yesterday", "Mon", "Jan 15"
 */
fun Long.toRelativeTimeString(): String {
    val now = Clock.System.now()
    val instant = Instant.fromEpochMilliseconds(this)
    val duration = now - instant

    return when {
        duration.inWholeMinutes < 1 -> "Just now"
        duration.inWholeMinutes < 60 -> "${duration.inWholeMinutes}m ago"
        duration.inWholeHours < 24 -> "${duration.inWholeHours}h ago"
        duration.inWholeDays < 2 -> "Yesterday"
        duration.inWholeDays < 7 -> {
            val dayOfWeek = instant.toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek
            dayOfWeek.name
                .take(3)
                .lowercase()
                .replaceFirstChar { it.uppercase() }
        }
        else -> {
            val localDate = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            val month =
                localDate.month.name
                    .take(3)
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
            "$month ${localDate.dayOfMonth}"
        }
    }
}

/**
 * Formats a duration in seconds into a call duration string (e.g., "1:23", "01:05:30").
 */
fun Long.toCallDurationString(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60

    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Truncates a string to the given max length, appending an ellipsis if truncated.
 */
fun String.ellipsize(maxLength: Int): String =
    if (length <= maxLength) {
        this
    } else {
        take(maxLength - 1) + "\u2026"
    }

/**
 * Returns a masked phone number for display (e.g., "+1 *** *** 1234").
 */
fun String.maskPhoneNumber(): String {
    if (length < 4) return this
    val visible = takeLast(4)
    val masked = dropLast(4).map { if (it.isDigit()) '*' else it }.joinToString("")
    return "$masked$visible"
}
