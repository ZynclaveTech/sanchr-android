package com.sanchr.feature.chats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class TypingBroadcasterTest {
    private val sent = mutableListOf<Boolean>()

    private fun TestScope.broadcaster() =
        TypingBroadcaster(backgroundScope, idleMillis = 1_500, refreshMillis = 2_000, now = { currentTime }) { sent += it }

    @Test
    fun `starting to type says so once, not once a key`() =
        runTest {
            val typing = broadcaster()

            repeat(5) {
                typing.onTextChanged(hasText = true)
                advanceTimeBy(100)
            }

            assertEquals(listOf(true), sent)
        }

    @Test
    fun `a pause says stopped`() =
        runTest {
            val typing = broadcaster()
            typing.onTextChanged(hasText = true)

            advanceTimeBy(1_501)

            assertEquals(listOf(true, false), sent)
        }

    /** The gap that left typing up after every message. */
    @Test
    fun `sending says stopped at once`() =
        runTest {
            val typing = broadcaster()
            typing.onTextChanged(hasText = true)

            typing.stop()

            assertEquals(listOf(true, false), sent)
            advanceTimeBy(5_000)
            assertEquals(listOf(true, false), sent, "and the pause timer does not say it again")
        }

    @Test
    fun `emptying the field says stopped`() =
        runTest {
            val typing = broadcaster()
            typing.onTextChanged(hasText = true)

            typing.onTextChanged(hasText = false)

            assertEquals(listOf(true, false), sent)
        }

    @Test
    fun `stopping when nothing was said says nothing`() =
        runTest {
            val typing = broadcaster()

            typing.stop()
            typing.onTextChanged(hasText = false)

            assertEquals(emptyList(), sent)
        }

    /** A receiver forgets a typing it has not heard again for a few seconds. */
    @Test
    fun `typing that carries on is repeated so the other side keeps showing it`() =
        runTest {
            val typing = broadcaster()

            repeat(50) {
                typing.onTextChanged(hasText = true)
                advanceTimeBy(100)
            }

            // At 0 s, 2 s and 4 s over five seconds of steady typing.
            assertEquals(listOf(true, true, true), sent)
        }

    @Test
    fun `typing again after a pause starts over`() =
        runTest {
            val typing = broadcaster()
            typing.onTextChanged(hasText = true)
            advanceTimeBy(1_501)

            typing.onTextChanged(hasText = true)

            assertEquals(listOf(true, false, true), sent)
        }
}
