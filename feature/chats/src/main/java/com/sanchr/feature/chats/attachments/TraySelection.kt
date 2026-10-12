package com.sanchr.feature.chats.attachments

/**
 * Which recent photos are picked for one batch, in the order they were tapped.
 *
 * A list rather than a set, as iOS learned: the send order is the tap order,
 * and each badge shows a photo's place in it.
 */
data class TraySelection(
    val ids: List<Long> = emptyList(),
    /** True from the long press that starts a selection until it is sent or cancelled. */
    val active: Boolean = false,
) {
    val atLimit: Boolean get() = ids.size >= LIMIT

    /** A photo's place in the batch, from zero, or null when it is not in it. */
    fun indexOf(id: Long): Int? = ids.indexOf(id).takeIf { it >= 0 }

    /** A long press: starts a selection holding just that photo. */
    fun startedWith(id: Long): TraySelection = TraySelection(ids = listOf(id), active = true)

    /**
     * A tap while selecting. Unpicking the last photo ends the selection, and
     * a pick past [LIMIT] is refused and reported.
     */
    fun toggled(id: Long): Toggle =
        when {
            id in ids -> {
                val rest = ids - id
                Toggle(if (rest.isEmpty()) TraySelection() else copy(ids = rest))
            }
            atLimit -> Toggle(this, refused = true)
            else -> Toggle(copy(ids = ids + id))
        }

    data class Toggle(
        val selection: TraySelection,
        val refused: Boolean = false,
    )

    companion object {
        /**
         * Ceiling on one batch: each photo is read and sent in turn, so an
         * unbounded selection stalls with no way out. iOS's limit.
         */
        const val LIMIT = 30
    }
}

/** Why the strip of recent photos has nothing in it. */
enum class TrayEmptyReason { LOADING, DENIED, NO_PHOTOS }

/** How much of the photo library the app may read. */
enum class MediaAccess { GRANTED, LIMITED, DENIED }

/**
 * What to say in place of the strip, or null when there are photos to show.
 *
 * @param recents null while the library is still being read.
 */
fun trayEmptyReason(
    access: MediaAccess,
    recents: List<*>?,
): TrayEmptyReason? =
    when {
        access == MediaAccess.DENIED -> TrayEmptyReason.DENIED
        recents == null -> TrayEmptyReason.LOADING
        recents.isEmpty() -> TrayEmptyReason.NO_PHOTOS
        else -> null
    }

/** `m:ss` under an hour and `h:mm:ss` over, with a dash for a length that is not known. */
fun trayDurationLabel(durationMs: Long): String {
    if (durationMs <= 0) return "—"
    val total = (durationMs + HALF_SECOND_MS) / SECOND_MS
    val hours = total / SECONDS_PER_HOUR
    val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private const val SECOND_MS = 1000L
private const val HALF_SECOND_MS = 500L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
