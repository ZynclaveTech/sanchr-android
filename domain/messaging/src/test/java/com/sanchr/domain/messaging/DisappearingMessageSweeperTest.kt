package com.sanchr.domain.messaging

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A disappearing message goes when its time is up, not at the next background sync. */
@OptIn(ExperimentalCoroutinesApi::class)
class DisappearingMessageSweeperTest {
    private val nextExpiry = MutableStateFlow<Long?>(null)
    private val repository = mockk<MessageRepository>(relaxed = true) { every { observeNextExpiryMillis() } returns nextExpiry }
    private val expire = mockk<ExpireMessagesUseCase>()
    private val sweeper = DisappearingMessageSweeper(repository, expire)

    init {
        // Removing what is due leaves nothing waiting.
        coEvery { expire(any()) } coAnswers {
            nextExpiry.value = null
            1
        }
    }

    @Test
    fun `nothing is removed before the deadline, and it is removed at it`() =
        runTest {
            val job = launch { sweeper.run(now = { currentTime }) }
            nextExpiry.value = 5_000

            advanceTimeBy(4_999)
            runCurrent()
            coVerify(exactly = 0) { expire(any()) }

            advanceTimeBy(1)
            runCurrent()
            coVerify(exactly = 1) { expire(5_000) }

            job.cancel()
        }

    @Test
    fun `a deadline already past is acted on at once`() =
        runTest {
            val job = launch { sweeper.run(now = { 9_000 }) }
            nextExpiry.value = 5_000

            runCurrent()

            coVerify(exactly = 1) { expire(9_000) }
            job.cancel()
        }

    /** A message arrives with a shorter timer than the one being waited on. */
    @Test
    fun `an earlier deadline that turns up is waited for instead`() =
        runTest {
            val job = launch { sweeper.run(now = { currentTime }) }
            nextExpiry.value = 60_000
            advanceTimeBy(1_000)

            nextExpiry.value = 3_000
            advanceTimeBy(2_000)
            runCurrent()

            coVerify(exactly = 1) { expire(3_000) }
            job.cancel()
        }

    @Test
    fun `with no disappearing messages it simply waits`() =
        runTest {
            val job = launch { sweeper.run(now = { currentTime }) }

            advanceTimeBy(3_600_000)
            runCurrent()

            coVerify(exactly = 0) { expire(any()) }
            job.cancel()
        }

    @Test
    fun `a failure does not stop it from acting on the next deadline`() =
        runTest {
            coEvery { expire(1_000) } throws RuntimeException("db")
            val job = launch { sweeper.run(now = { currentTime }) }
            nextExpiry.value = 1_000
            advanceTimeBy(1_000)
            runCurrent()

            nextExpiry.value = 2_000
            advanceTimeBy(1_000)
            runCurrent()

            coVerify(exactly = 1) { expire(2_000) }
            job.cancel()
        }
}
