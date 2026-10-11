package com.sanchr.feature.chats

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tells the other side when this person starts typing — and, as reliably,
 * when they stop.
 *
 * The stop was the part missing. "Typing" was sent on every keystroke and
 * "not typing" only if the field was emptied by hand. Sending a message
 * empties it without passing through here, so the last thing the other phone
 * heard after every sent message was "typing"; leaving the chat said nothing
 * either. With no expiry on the receiving side, "typing…" stayed up until the
 * next keystroke, whenever that was.
 *
 * Now, as iOS does it: "typing" when it starts, "stopped" after a pause, on
 * send, and on leaving. While typing carries on it is repeated every
 * [refreshMillis], because a receiver rightly forgets a "typing" it has not
 * heard again for a few seconds.
 *
 * Not thread-safe; driven from the view model's main-thread calls.
 */
internal class TypingBroadcaster(
    private val scope: CoroutineScope,
    private val idleMillis: Long = IDLE_MILLIS,
    private val refreshMillis: Long = REFRESH_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
    private val send: (isTyping: Boolean) -> Unit,
) {
    private var active = false
    private var lastSentTyping = 0L
    private var idleStop: Job? = null

    /** Called on every change to the composer's text. */
    fun onTextChanged(hasText: Boolean) {
        if (!hasText) {
            stop()
            return
        }
        val current = now()
        if (!active || current - lastSentTyping >= refreshMillis) {
            active = true
            lastSentTyping = current
            send(true)
        }
        idleStop?.cancel()
        idleStop =
            scope.launch {
                delay(idleMillis)
                stop()
            }
    }

    /** Sending, leaving the chat, or the setting being turned off. Says so once. */
    fun stop() {
        idleStop?.cancel()
        idleStop = null
        if (!active) return
        active = false
        send(false)
    }

    private companion object {
        /** iOS's pause before "stopped". */
        const val IDLE_MILLIS = 1_500L

        /** Under the receivers' expiry, so an indicator does not flicker while typing carries on. */
        const val REFRESH_MILLIS = 2_000L
    }
}
