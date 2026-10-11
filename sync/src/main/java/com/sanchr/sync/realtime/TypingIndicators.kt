package com.sanchr.sync.realtime

import com.sanchr.proto.messaging.TypingIndicator
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Who is typing, by conversation.
 *
 * A "typing" is believed for [expiryMillis] and no longer. It used to be
 * believed until a "stopped" arrived, and one that never came — the sender
 * went offline, closed the app, or simply did not send it — left "typing…" on
 * the chat and on its row in the list for as long as the app stayed open.
 */
internal class TypingIndicators(
    private val scope: CoroutineScope,
    private val expiryMillis: Long = EXPIRY_MILLIS,
) {
    private val _active = MutableStateFlow<Map<String, TypingIndicator>>(emptyMap())
    val active: StateFlow<Map<String, TypingIndicator>> = _active.asStateFlow()

    private val expiry = HashMap<String, Job>()

    fun onIndicator(indicator: TypingIndicator?) {
        if (indicator == null || indicator.conversationId.isBlank()) return
        val conversationId = indicator.conversationId
        synchronized(expiry) {
            expiry.remove(conversationId)?.cancel()
            if (indicator.isTyping) {
                expiry[conversationId] =
                    scope.launch {
                        delay(expiryMillis)
                        // Only if this is still the timer in charge: a newer
                        // "typing" cancels this one and sets its own. Told
                        // apart by the timer and not by the indicator, which
                        // is equal to the one it replaced and so is not
                        // swapped in by the state flow at all.
                        val inCharge = synchronized(expiry) { expiry.remove(conversationId, coroutineContext[Job]) }
                        if (inCharge) _active.update { it - conversationId }
                    }
            }
        }
        _active.update { current -> if (indicator.isTyping) current + (conversationId to indicator) else current - conversationId }
    }

    private companion object {
        /**
         * Longer than the two seconds a sender repeats "typing" at while it
         * carries on, so the indicator holds steady; short enough that one
         * left behind is gone before anyone wonders about it.
         */
        const val EXPIRY_MILLIS = 6_000L
    }
}
