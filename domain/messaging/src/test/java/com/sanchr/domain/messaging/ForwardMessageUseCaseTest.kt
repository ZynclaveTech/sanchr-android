package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.core.model.MessageStatus
import com.sanchr.domain.messaging.media.AttachmentDownloader
import com.sanchr.domain.messaging.media.AttachmentUploader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ForwardMessageUseCaseTest {
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }
    private val sendText = mockk<SendMessageUseCase>()
    private val sendAttachment = mockk<SendAttachmentUseCase>()
    private val downloader = mockk<AttachmentDownloader>()
    private val useCase = ForwardMessageUseCase(sendText, sendAttachment, downloader, dispatchers)
    private val sent = mockk<Message>()

    private fun message(content: MessageContent) =
        Message(
            id = "m1",
            conversationId = "c0",
            senderId = "peer",
            content = content,
            status = MessageStatus.DELIVERED,
            timestamp = Instant.fromEpochMilliseconds(1),
        )

    @Test
    fun `text goes to every distinct destination and failures are counted, not thrown`() =
        runTest {
            coEvery { sendText("c1", "hi", any(), any()) } returns Result.Success(sent)
            coEvery { sendText("c2", "hi", any(), any()) } throws IOException("offline")

            val outcome = useCase(message(MessageContent.Text("hi")), listOf("c1", "c2", "c1", ""))

            assertEquals(1, outcome.sent)
            assertEquals(1, outcome.failed)
            coVerify(exactly = 1) { sendText("c1", "hi", any(), any()) }
        }

    @Test
    fun `media is decrypted once and uploaded per destination with its metadata`() =
        runTest {
            val file =
                File.createTempFile("fwd", ".jpg").apply {
                    writeBytes(byteArrayOf(1, 2, 3))
                    deleteOnExit()
                }
            val attachment =
                MediaAttachment(
                    "sanchr-media://m",
                    "AQ==",
                    "AQ==",
                    "image/jpeg",
                    3,
                    width = 4,
                    height = 3,
                    filename = "p.jpg",
                    blurHash = "LEHV6",
                )
            coEvery { downloader.open("m1", attachment) } returns file
            val prepared = mutableListOf<AttachmentUploader.Prepared>()
            coEvery { sendAttachment(any(), capture(prepared)) } returns Result.Success(sent)

            val outcome =
                useCase(
                    message(MessageContent.Image("sanchr-media://m", null, 4, 3, caption = "look", attachment = attachment)),
                    listOf("c1", "c2"),
                )

            assertEquals(2, outcome.sent)
            coVerify(exactly = 1) { downloader.open("m1", attachment) }
            assertEquals(2, prepared.size)
            assertEquals("look", prepared[0].caption)
            assertEquals("p.jpg", prepared[0].fileName)
            assertEquals("LEHV6", prepared[0].blurHash)
            assertEquals(listOf<Byte>(1, 2, 3), prepared[0].bytes.toList())
        }

    @Test
    fun `view-once media and tombstones cannot be forwarded`() =
        runTest {
            val once = MediaAttachment("sanchr-media://m", "AQ==", "AQ==", "image/jpeg", 3, isViewOnce = true)
            assertThrows(UnsupportedContentException::class.java) {
                kotlinx.coroutines.runBlocking { useCase(message(MessageContent.Image("u", null, 1, 1, attachment = once)), listOf("c1")) }
            }
            assertThrows(UnsupportedContentException::class.java) {
                kotlinx.coroutines.runBlocking { useCase(message(MessageContent.System("viewOnceConsumed")), listOf("c1")) }
            }
        }
}
