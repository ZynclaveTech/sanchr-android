package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.model.Message
import com.sanchr.domain.messaging.media.AttachmentUploader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Several photos sent as one message.
 *
 * A batch used to be one message per file, so picking ten photos produced ten
 * bubbles on both platforms and ten notifications for the recipient, where
 * iOS produces a single collage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendAlbumUseCaseTest {
    private val dispatcher = StandardTestDispatcher()
    private val uploader = mockk<AttachmentUploader>()
    private val sendMessageUseCase = mockk<SendMessageUseCase>()

    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher get() = dispatcher
            override val io: CoroutineDispatcher get() = dispatcher
            override val default: CoroutineDispatcher get() = dispatcher
            override val unconfined: CoroutineDispatcher get() = dispatcher
            override val signalDispatcher: CoroutineDispatcher get() = dispatcher
        }

    private val useCase = SendAlbumUseCase(uploader, sendMessageUseCase, dispatchers)

    private fun prepared(name: String) =
        AttachmentUploader.Prepared(
            bytes = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            fileName = name,
        )

    private fun uploaded(url: String) = MediaAttachment(url = url, mimeType = "image/jpeg", sizeBytes = 3)

    @Test
    fun `every attachment travels in one message`() =
        runTest(dispatcher) {
            coEvery { uploader.upload(any(), any()) } returnsMany listOf(uploaded("a"), uploaded("b"), uploaded("c"))
            val body = slot<String>()
            coEvery {
                sendMessageUseCase(any(), capture(body), any(), any())
            } returns Result.Success(mockk<Message>(relaxed = true))

            val result = useCase("conv-1", listOf(prepared("a.jpg"), prepared("b.jpg"), prepared("c.jpg")))

            assertIs<Result.Success<Message>>(result)
            coVerify(exactly = 1) { sendMessageUseCase(any(), any(), any(), any()) }
            // All three in the one envelope the receive path already reads.
            assertTrue(body.captured.contains("\"a\""), body.captured)
            assertTrue(body.captured.contains("\"b\""), body.captured)
            assertTrue(body.captured.contains("\"c\""), body.captured)
        }

    @Test
    fun `progress runs across the whole set, not per file`() =
        runTest(dispatcher) {
            val progress = mutableListOf<Float>()
            coEvery { uploader.upload(any(), any()) } answers {
                secondArg<((Float) -> Unit)?>()?.invoke(1f)
                uploaded("x")
            }
            coEvery { sendMessageUseCase(any(), any(), any(), any()) } returns Result.Success(mockk<Message>(relaxed = true))

            useCase("conv-1", listOf(prepared("a.jpg"), prepared("b.jpg"))) { progress += it }

            assertEquals(listOf(0.5f, 1f), progress)
        }

    @Test
    fun `a failed upload fails the album rather than sending part of it`() =
        runTest(dispatcher) {
            coEvery { uploader.upload(any(), any()) } returns uploaded("a") andThenThrows IllegalStateException("offline")

            val result = useCase("conv-1", listOf(prepared("a.jpg"), prepared("b.jpg")))

            assertIs<Result.Error>(result)
            // A partial album is not a smaller album, it is a wrong one.
            coVerify(exactly = 0) { sendMessageUseCase(any(), any(), any(), any()) }
        }

    @Test
    fun `one attachment is not an album`() =
        runTest(dispatcher) {
            val result = useCase("conv-1", listOf(prepared("only.jpg")))

            assertIs<Result.Error>(result)
            coVerify(exactly = 0) { uploader.upload(any(), any()) }
        }
}
