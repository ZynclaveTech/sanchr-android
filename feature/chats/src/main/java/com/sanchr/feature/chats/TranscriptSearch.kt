package com.sanchr.feature.chats

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Search within one conversation: debounced, wrapping, newest first — iOS's
 * `scheduleSearch`.
 *
 * Its own class because the view model had grown past what detekt will accept
 * and this is the most self-contained piece in it: five transitions over one
 * field of the state, and a debounce job. It is also the part worth testing on
 * its own — the wrap-around arithmetic and the stale-result guard are where a
 * mistake would be invisible until someone searched a long chat.
 */
internal class TranscriptSearch(
    private val state: MutableStateFlow<ChatDetailUiState>,
    private val scope: CoroutineScope,
    private val debounceMillis: Long,
    /** Message ids matching a query, newest first. */
    private val findMatches: suspend (String) -> List<String>,
) {
    private var job: Job? = null

    fun open() {
        state.update { it.copy(search = it.search ?: ChatSearchState()) }
    }

    fun close() {
        job?.cancel()
        state.update { it.copy(search = null) }
    }

    fun onQueryChanged(query: String) {
        state.update { it.copy(search = (it.search ?: ChatSearchState()).copy(query = query)) }
        job?.cancel()
        if (query.isBlank()) {
            state.update { it.copy(search = it.search?.copy(resultIds = emptyList(), currentIndex = 0)) }
            return
        }
        job =
            scope.launch {
                delay(debounceMillis)
                val ids = runCatching { findMatches(query) }.getOrDefault(emptyList())
                state.update { current ->
                    val search = current.search ?: return@update current
                    // Dropped when the query has moved on: a slow search for
                    // an earlier term would otherwise land on top of a later
                    // one's results.
                    if (search.query != query) current else current.copy(search = search.copy(resultIds = ids, currentIndex = 0))
                }
            }
    }

    fun next() = step(+1)

    fun previous() = step(-1)

    /** Wraps in both directions, so the arrows never dead-end. */
    private fun step(delta: Int) {
        state.update { current ->
            val search = current.search ?: return@update current
            if (search.resultIds.isEmpty()) return@update current
            val size = search.resultIds.size
            current.copy(search = search.copy(currentIndex = ((search.currentIndex + delta) % size + size) % size))
        }
    }
}
