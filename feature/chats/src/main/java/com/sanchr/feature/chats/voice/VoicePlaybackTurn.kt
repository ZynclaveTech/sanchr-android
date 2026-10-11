package com.sanchr.feature.chats.voice

/**
 * Whose turn it is to play.
 *
 * Each voice note owns its own player, and nothing told one that another had
 * started: play a second note without pausing the first and the two played
 * over each other. Starting one now takes the turn, and whoever held it is
 * told to stop. iOS has one shared controller for the same reason.
 *
 * Main-thread only, like the composables that use it.
 */
internal object VoicePlaybackTurn {
    private var holder: Any? = null
    private var stopHolder: (() -> Unit)? = null

    /** Takes the turn for [owner], stopping whoever had it. */
    fun take(
        owner: Any,
        stop: () -> Unit,
    ) {
        if (holder !== owner) stopHolder?.invoke()
        holder = owner
        stopHolder = stop
    }

    /** Gives the turn up, if [owner] still has it. */
    fun release(owner: Any) {
        if (holder === owner) {
            holder = null
            stopHolder = null
        }
    }

    /** Where in a clip a touch on its waveform lands. */
    fun positionFor(
        touchX: Float,
        width: Float,
        durationMs: Int,
    ): Int {
        if (width <= 0f || durationMs <= 0) return 0
        return ((touchX / width).coerceIn(0f, 1f) * durationMs).toInt()
    }
}
