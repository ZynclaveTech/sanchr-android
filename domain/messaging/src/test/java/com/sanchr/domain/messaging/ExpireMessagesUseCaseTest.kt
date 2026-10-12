package com.sanchr.domain.messaging

import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.core.model.MessageStatus
import com.sanchr.domain.messaging.media.AttachmentDownloader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/** A disappearing message that expires takes its decrypted media with it. */
class ExpireMessagesUseCaseTest {
    private val repository = mockk<MessageRepository>(relaxed = true)
    private val downloader = mockk<AttachmentDownloader>(relaxed = true)
    private val expire = ExpireMessagesUseCase(repository, MessageMediaEvictor(repository, downloader))

    private fun photoMessage(id: String) =
        Message(
            id = id,
            conversationId = "c1",
            senderId = "peer",
            content =
                MessageContent.Image(
                    url = "",
                    thumbnailUrl = null,
                    width = 1,
                    height = 1,
                    attachment = MediaAttachment(url = "sanchr-media://x", mimeType = "image/jpeg", sizeBytes = 1),
                ),
            status = MessageStatus.DELIVERED,
            timestamp = Instant.fromEpochMilliseconds(1),
        )

    @Test
    fun `an expired photo's decrypted copy is removed, and before its row is`() =
        runTest {
            coEvery { repository.expiredMessageIds(500L) } returns listOf("p1")
            coEvery { repository.message("p1") } returns photoMessage("p1")
            coEvery { repository.deleteExpiredMessages(500L) } returns 1

            assertEquals(1, expire(500L))

            coVerifyOrder {
                downloader.evict("p1", "image/jpeg")
                repository.deleteExpiredMessages(500L)
            }
        }

    /**
     * A chat that is open can ask for the picture again between the first
     * removal and the row going. Nothing asks once the row is gone.
     */
    @Test
    fun `the copy is removed again once the row is gone`() =
        runTest {
            coEvery { repository.expiredMessageIds(500L) } returns listOf("p1")
            coEvery { repository.message("p1") } returns photoMessage("p1")
            coEvery { repository.deleteExpiredMessages(500L) } returns 1

            expire(500L)

            coVerifyOrder {
                downloader.evict("p1", "image/jpeg")
                repository.deleteExpiredMessages(500L)
                downloader.evict("p1", "image/jpeg")
            }
            coVerify(exactly = 2) { downloader.evict("p1", "image/jpeg") }
        }

    @Test
    fun `nothing due means nothing is touched`() =
        runTest {
            coEvery { repository.expiredMessageIds(any()) } returns emptyList()

            assertEquals(0, expire(500L))

            coVerify(exactly = 0) { repository.deleteExpiredMessages(any()) }
            coVerify(exactly = 0) { downloader.evict(any(), any()) }
        }

    /** A file that will not delete must not be what keeps the message. */
    @Test
    fun `a file that cannot be removed does not keep the message`() =
        runTest {
            coEvery { repository.expiredMessageIds(500L) } returns listOf("p1")
            coEvery { repository.message("p1") } returns photoMessage("p1")
            coEvery { downloader.evict(any(), any()) } throws RuntimeException("disk")
            coEvery { repository.deleteExpiredMessages(500L) } returns 1

            assertEquals(1, expire(500L))
        }
}
