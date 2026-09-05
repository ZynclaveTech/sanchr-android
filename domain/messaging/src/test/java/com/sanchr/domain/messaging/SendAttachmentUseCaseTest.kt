package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.model.MediaContentEnvelope
import com.sanchr.core.model.MediaKind
import com.sanchr.core.model.Message
import com.sanchr.domain.messaging.media.AttachmentUploader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

class SendAttachmentUseCaseTest {
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }
    private val uploader = mockk<AttachmentUploader>()
    private val sender = mockk<SendMessageUseCase>()
    private val message = mockk<Message>()

    @Test
    fun `sends the iOS envelope with the media kind as the content type`() =
        runTest {
            val attachment = MediaAttachment("sanchr-media://m", "AQ==", "AQ==", "video/mp4", 5)
            coEvery { uploader.upload(any()) } returns attachment
            val body = slot<String>()
            val type = slot<String>()
            coEvery { sender.invoke("conv", capture(body), capture(type)) } returns Result.Success(message)

            val result =
                SendAttachmentUseCase(
                    uploader,
                    sender,
                    dispatchers,
                )("conv", AttachmentUploader.Prepared(byteArrayOf(1), "video/mp4", "v.mp4"))

            assertTrue(result is Result.Success)
            assertEquals("video", type.captured)
            val decoded = requireNotNull(MediaContentEnvelope.decode(body.captured, null))
            assertEquals(MediaKind.VIDEO, decoded.kind)
            assertEquals("sanchr-media://m", decoded.attachments.single().url)
        }

    @Test
    fun `an upload failure is a send failure, and nothing is sent`() =
        runTest {
            coEvery { uploader.upload(any()) } throws IllegalStateException("HTTP 500")

            val result =
                SendAttachmentUseCase(
                    uploader,
                    sender,
                    dispatchers,
                )("conv", AttachmentUploader.Prepared(byteArrayOf(1), "image/png", "p.png"))

            assertTrue(result is Result.Error)
        }

    @Test
    fun `an attachment sent as a reply carries the quoted message id, and no reply sends none`() =
        runTest {
            val attachment = MediaAttachment("sanchr-media://m", "AQ==", "AQ==", "image/jpeg", 5)
            coEvery { uploader.upload(any()) } returns attachment
            coEvery { sender.invoke("conv", any(), any(), any()) } returns Result.Success(message)
            val useCase = SendAttachmentUseCase(uploader, sender, dispatchers)
            val prepared = AttachmentUploader.Prepared(byteArrayOf(1), "image/jpeg", "p.jpg")

            useCase("conv", prepared, replyToId = "m-quoted")
            useCase("conv", prepared)

            coVerify(exactly = 1) { sender.invoke("conv", any(), "image", "m-quoted") }
            coVerify(exactly = 1) { sender.invoke("conv", any(), "image", null) }
        }
}
