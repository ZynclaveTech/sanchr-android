package com.sanchr.feature.chats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * Search within a conversation: debounced, wrapping, and never showing a
 * stale query's results.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptSearchTest {
    private val debounce = 300L

    private fun fixture(
        scope: TestScope,
        matches: Map<String, List<String>> = emptyMap(),
        onQuery: (String) -> Unit = {},
    ): Pair<MutableStateFlow<ChatDetailUiState>, TranscriptSearch> {
        val state = MutableStateFlow(ChatDetailUiState())
        val search =
            TranscriptSearch(
                state = state,
                scope = scope,
                debounceMillis = debounce,
                findMatches = { query ->
                    onQuery(query)
                    matches[query].orEmpty()
                },
            )
        return state to search
    }

    @Test
    fun `opening starts a search and closing clears it`() =
        runTest {
            val (state, search) = fixture(this)

            search.open()
            assertEquals("", state.value.search?.query)

            search.close()
            assertNull(state.value.search)
        }

    @Test
    fun `typing is debounced, so one query runs rather than one per keystroke`() =
        runTest {
            val queried = mutableListOf<String>()
            val (state, search) = fixture(this, matches = mapOf("hello" to listOf("m3", "m1")), onQuery = { queried += it })

            search.open()
            search.onQueryChanged("hel")
            search.onQueryChanged("hell")
            search.onQueryChanged("hello")
            advanceUntilIdle()

            assertEquals(listOf("hello"), queried)
            assertEquals(listOf("m3", "m1"), state.value.search?.resultIds)
        }

    @Test
    fun `clearing the query drops the results without running a search`() =
        runTest {
            val queried = mutableListOf<String>()
            val (state, search) = fixture(this, matches = mapOf("x" to listOf("m1")), onQuery = { queried += it })
            search.open()
            search.onQueryChanged("x")
            advanceUntilIdle()

            search.onQueryChanged("  ")
            advanceUntilIdle()

            assertTrue(
                state.value.search
                    ?.resultIds
                    .orEmpty()
                    .isEmpty(),
            )
            assertEquals(listOf("x"), queried)
        }

    @Test
    fun `the arrows wrap in both directions rather than dead-ending`() =
        runTest {
            val (state, search) = fixture(this, matches = mapOf("q" to listOf("a", "b", "c")))
            search.open()
            search.onQueryChanged("q")
            advanceUntilIdle()

            search.previous()
            assertEquals(2, state.value.search?.currentIndex, "going back from the first must wrap to the last")

            search.next()
            assertEquals(0, state.value.search?.currentIndex, "going forward from the last must wrap to the first")
        }

    @Test
    fun `stepping with no results is a no-op rather than an index error`() =
        runTest {
            val (state, search) = fixture(this)
            search.open()

            search.next()
            search.previous()

            assertEquals(0, state.value.search?.currentIndex)
        }

    @Test
    fun `a slow search for an earlier term does not land on a later one's results`() =
        runTest {
            val (state, search) =
                fixture(this, matches = mapOf("old" to listOf("stale"), "new" to listOf("fresh")))
            search.open()

            search.onQueryChanged("old")
            advanceTimeBy(debounce / 2)
            search.onQueryChanged("new")
            advanceUntilIdle()

            assertEquals(listOf("fresh"), state.value.search?.resultIds)
        }
}
