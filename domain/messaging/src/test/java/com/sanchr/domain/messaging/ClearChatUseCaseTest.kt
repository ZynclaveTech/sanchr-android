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
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/** "Permanently delete all messages" has to mean the pictures too. */
class ClearChatUseCaseTest {
    private val repository = mockk<MessageRepository>(relaxed = true)
    private val downloader = mockk<AttachmentDownloader>(relaxed = true)
    private val clearChat = ClearChatUseCase(repository, MessageMediaEvictor(repository, downloader))

    private fun attachment(mimeType: String) = MediaAttachment(url = "sanchr-media://x", mimeType = mimeType, sizeBytes = 1)

    private fun photo() = MessageContent.Image(url = "", thumbnailUrl = null, width = 1, height = 1, attachment = attachment("image/jpeg"))

    private fun message(
        id: String,
        content: MessageContent,
    ) = Message(
        id = id,
        conversationId = "c1",
        senderId = "peer",
        content = content,
        status = MessageStatus.DELIVERED,
        timestamp = Instant.fromEpochMilliseconds(1),
    )

    private fun holds(vararg messages: Message) {
        coEvery { repository.messageIds("c1") } returns messages.map { it.id }
        messages.forEach { coEvery { repository.message(it.id) } returns it }
    }

    @Test
    fun `every message goes, and the count says how many`() =
        runTest {
            holds(message("t1", MessageContent.Text("hi")), message("t2", MessageContent.Text("there")))

            assertEquals(2, clearChat("c1"))

            coVerify(exactly = 1) { repository.clearMessages("c1") }
        }

    @Test
    fun `a photo's decrypted copy goes with its message`() =
        runTest {
            holds(message("p1", photo()))

            clearChat("c1")

            coVerify(exactly = 1) { downloader.evict("p1", "image/jpeg") }
        }

    /** Each tile of an album is cached under its own id. */
    @Test
    fun `every picture of an album goes, not just the first`() =
        runTest {
            holds(message("a1", MessageContent.Album(listOf(attachment("image/jpeg"), attachment("image/png"), attachment("video/mp4")))))

            clearChat("c1")

            coVerify(exactly = 1) { downloader.evict("a1#0", "image/jpeg") }
            coVerify(exactly = 1) { downloader.evict("a1#1", "image/png") }
            coVerify(exactly = 1) { downloader.evict("a1#2", "video/mp4") }
        }

    /** The rows say which files there are, so the files go first. */
    @Test
    fun `the files are removed while the rows still say which they are`() =
        runTest {
            holds(message("p1", photo()))

            clearChat("c1")

            coVerifyOrder {
                repository.message("p1")
                downloader.evict("p1", "image/jpeg")
                repository.clearMessages("c1")
            }
        }

    @Test
    fun `a file that will not delete does not keep the messages on the phone`() =
        runTest {
            holds(
                message("p1", photo()),
                message("p2", photo()),
            )
            coEvery { downloader.evict("p1", any()) } throws IllegalStateException("io")

            clearChat("c1")

            coVerify(exactly = 1) { downloader.evict("p2", "image/jpeg") }
            coVerify(exactly = 1) { repository.clearMessages("c1") }
        }

    @Test
    fun `an empty chat is cleared without complaint`() =
        runTest {
            holds()

            assertEquals(0, clearChat("c1"))
            coVerify(exactly = 1) { repository.clearMessages("c1") }
        }

    @Test
    fun `cancellation is not swallowed half way`() =
        runTest {
            holds(message("p1", photo()))
            coEvery { downloader.evict(any(), any()) } throws CancellationException("gone")

            assertFailsWith<CancellationException> { clearChat("c1") }
            coVerify(exactly = 0) { repository.clearMessages(any()) }
        }
}
