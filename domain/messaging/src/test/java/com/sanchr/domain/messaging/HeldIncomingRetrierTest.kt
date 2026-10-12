package com.sanchr.domain.messaging

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

/** A message that could not be filed when it arrived is filed later, not lost. */
class HeldIncomingRetrierTest {
    private val repository = mockk<MessageRepository>(relaxed = true)
    private val receive = mockk<ReceiveMessageUseCase>(relaxed = true)
    private val retrier = HeldIncomingRetrier(repository, receive)

    private fun held(
        id: Long,
        attempts: Int = 0,
    ) = HeldIncoming(
        id = id,
        decrypted = EnvelopeDecryptResult.Success("alice", 1, byteArrayOf(id.toByte()), 42L),
        envelope = IncomingEnvelopeContext("env-conv", "env-$id", "text"),
        attempts = attempts,
    )

    @Test
    fun `what can now be filed is filed and let go`() =
        runTest {
            val first = held(1)
            val second = held(2)
            coEvery { repository.heldIncoming() } returns listOf(first, second)

            assertEquals(2, retrier.retry())

            coVerify(exactly = 1) { receive.file(first.decrypted, first.envelope) }
            coVerify(exactly = 1) { receive.file(second.decrypted, second.envelope) }
            coVerify(exactly = 1) { repository.releaseIncoming(1) }
            coVerify(exactly = 1) { repository.releaseIncoming(2) }
        }

    @Test
    fun `what still cannot be filed is kept, and does not stop the rest`() =
        runTest {
            val stuck = held(1)
            val fine = held(2)
            coEvery { repository.heldIncoming() } returns listOf(stuck, fine)
            coEvery { receive.file(stuck.decrypted, stuck.envelope) } throws java.io.IOException("still broken")

            assertEquals(1, retrier.retry())

            coVerify(exactly = 0) { repository.releaseIncoming(1) }
            coVerify(exactly = 1) { repository.noteHeldAttempt(1) }
            coVerify(exactly = 1) { repository.releaseIncoming(2) }
        }

    /** Not carried and retried for ever. */
    @Test
    fun `something that has failed too many times is let go`() =
        runTest {
            val hopeless = held(1, attempts = HeldIncomingRetrier.MAX_ATTEMPTS - 1)
            coEvery { repository.heldIncoming() } returns listOf(hopeless)
            coEvery { receive.file(any(), any()) } throws java.io.IOException("never")

            assertEquals(0, retrier.retry())

            coVerify(exactly = 1) { repository.releaseIncoming(1) }
            coVerify(exactly = 0) { repository.noteHeldAttempt(any()) }
        }

    @Test
    fun `with nothing held, nothing happens`() =
        runTest {
            coEvery { repository.heldIncoming() } returns emptyList()

            assertEquals(0, retrier.retry())

            coVerify(exactly = 0) { receive.file(any(), any()) }
        }

    @Test
    fun `being cancelled is not a failed attempt`() =
        runTest {
            coEvery { repository.heldIncoming() } returns listOf(held(1))
            coEvery { receive.file(any(), any()) } throws CancellationException("stopped")

            assertFailsWith<CancellationException> { retrier.retry() }

            coVerify(exactly = 0) { repository.noteHeldAttempt(any()) }
            coVerify(exactly = 0) { repository.releaseIncoming(any()) }
        }
}
