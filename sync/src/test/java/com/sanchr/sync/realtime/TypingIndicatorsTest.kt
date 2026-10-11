package com.sanchr.sync.realtime

import com.sanchr.proto.messaging.TypingIndicator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class TypingIndicatorsTest {
    private fun typing(
        conversation: String,
        isTyping: Boolean = true,
    ) = TypingIndicator(conversationId = conversation, userId = "peer", isTyping = isTyping)

    @Test
    fun `typing shows, and stopped takes it down`() =
        runTest {
            val indicators = TypingIndicators(backgroundScope, expiryMillis = 6_000)

            indicators.onIndicator(typing("c1"))
            assertEquals(setOf("c1"), indicators.active.value.keys)

            indicators.onIndicator(typing("c1", isTyping = false))
            assertTrue(indicators.active.value.isEmpty())
        }

    /** The sender went offline, or just never said so. */
    @Test
    fun `typing that is never followed by stopped lapses`() =
        runTest {
            val indicators = TypingIndicators(backgroundScope, expiryMillis = 6_000)
            indicators.onIndicator(typing("c1"))

            advanceTimeBy(5_999)
            assertEquals(setOf("c1"), indicators.active.value.keys)

            advanceTimeBy(2)
            assertTrue(indicators.active.value.isEmpty())
        }

    @Test
    fun `typing that is repeated stays up, counted from the last time it was heard`() =
        runTest {
            val indicators = TypingIndicators(backgroundScope, expiryMillis = 6_000)
            indicators.onIndicator(typing("c1"))
            advanceTimeBy(4_000)
            indicators.onIndicator(typing("c1"))

            advanceTimeBy(4_000)
            assertEquals(setOf("c1"), indicators.active.value.keys)

            advanceTimeBy(2_001)
            assertTrue(indicators.active.value.isEmpty())
        }

    @Test
    fun `one conversation lapsing leaves another alone`() =
        runTest {
            val indicators = TypingIndicators(backgroundScope, expiryMillis = 6_000)
            indicators.onIndicator(typing("c1"))
            advanceTimeBy(3_000)
            indicators.onIndicator(typing("c2"))

            advanceTimeBy(3_001)

            assertEquals(setOf("c2"), indicators.active.value.keys)
        }

    @Test
    fun `an indicator that names no conversation is ignored`() =
        runTest {
            val indicators = TypingIndicators(backgroundScope)

            indicators.onIndicator(typing(""))
            indicators.onIndicator(null)

            assertTrue(indicators.active.value.isEmpty())
        }
}
