package com.sanchr.domain.messaging

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.model.MediaAttachment
import com.sanchr.domain.messaging.media.AttachmentDownloader
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ConsumeViewOnceUseCaseTest {
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
            override val signalDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        }
    private val repo = mockk<MessageRepository>(relaxed = true)
    private val downloader = mockk<AttachmentDownloader> { coEvery { evict(any(), any()) } returns true }

    @Test
    fun `the cached bytes are wiped before the row becomes a tombstone`() =
        runTest {
            val attachment = MediaAttachment(url = "sanchr-media://m", mimeType = "image/jpeg", isViewOnce = true)

            ConsumeViewOnceUseCase(repo, downloader, dispatchers)("m1", attachment)

            coVerifyOrder {
                downloader.evict("m1", "image/jpeg")
                repo.tombstoneViewOnce("m1")
            }
        }
}
