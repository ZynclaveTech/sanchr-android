package com.sanchr.feature.vault

import com.sanchr.core.common.Result
import com.sanchr.core.model.Message
import com.sanchr.core.model.VaultItem
import com.sanchr.core.model.VaultItemType
import com.sanchr.domain.messaging.ObserveConversationsUseCase
import com.sanchr.domain.messaging.SendAttachmentUseCase
import com.sanchr.domain.messaging.media.AttachmentUploader
import com.sanchr.domain.vault.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant

/**
 * Sharing a vault item out of the vault.
 *
 * The two destinations are not equivalent and the tests keep them apart:
 * "share in chat" must stay in memory and go through the ordinary encrypted
 * send path, while "share outside" hands plaintext to another app and is the
 * one the user is warned about.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultShareTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = mockk<VaultRepository>()
    private val send = mockk<SendAttachmentUseCase>()

    private val item =
        VaultItem(
            id = "v1",
            mediaId = "m-v1",
            type = VaultItemType.DOCUMENT,
            name = "statement.pdf",
            mimeType = "application/pdf",
            sizeBytes = 12,
            thumbnailJpeg = null,
            createdAt = Instant.fromEpochMilliseconds(1),
        )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        VaultViewModel(
            vaultRepository = repo,
            sendAttachmentUseCase = send,
            observeConversationsUseCase =
                mockk<ObserveConversationsUseCase>(relaxed = true) {
                    every { this@mockk() } returns flowOf(Result.Success(emptyList()))
                },
        )

    @Test
    fun `beginning a share names the item, and cancelling clears it`() =
        runTest(dispatcher) {
            coEvery { repo.listItems(any(), any()) } returns
                com.sanchr.domain.vault
                    .VaultPage(emptyList(), "")
            val vm = viewModel()
            advanceUntilIdle()

            vm.beginShare(item)
            assertEquals(item, vm.sharing.value)

            vm.cancelShare()
            assertNull(vm.sharing.value)
        }

    /**
     * The item is sent as an ordinary attachment: same encryption, same
     * pipeline, nothing vault-shaped on the wire. A recipient's client does
     * not need to know the vault exists.
     */
    @Test
    fun `sharing in chat sends the decrypted bytes through the ordinary send path`() =
        runTest(dispatcher) {
            coEvery { repo.listItems(any(), any()) } returns
                com.sanchr.domain.vault
                    .VaultPage(emptyList(), "")
            coEvery { repo.download(item) } returns byteArrayOf(1, 2, 3)
            val prepared = slot<AttachmentUploader.Prepared>()
            coEvery { send(any(), capture(prepared), any(), any()) } returns Result.Success(mockk<Message>())

            val vm = viewModel()
            advanceUntilIdle()
            vm.beginShare(item)
            vm.shareInChat(item, "conversation-1")
            advanceUntilIdle()

            assertNull(vm.sharing.value, "the sheet should close on send")
            coVerify { send("conversation-1", any(), null, any()) }
            assertEquals("application/pdf", prepared.captured.mimeType)
            assertEquals("statement.pdf", prepared.captured.fileName)
            assertEquals(listOf<Byte>(1, 2, 3), prepared.captured.bytes.toList())
        }

    @Test
    fun `a failed send says so rather than reporting success`() =
        runTest(dispatcher) {
            coEvery { repo.listItems(any(), any()) } returns
                com.sanchr.domain.vault
                    .VaultPage(emptyList(), "")
            coEvery { repo.download(item) } returns byteArrayOf(1)
            coEvery { send(any(), any(), any(), any()) } returns Result.Error(IllegalStateException("offline"))

            val vm = viewModel()
            val events = mutableListOf<VaultEvent>()
            val collector = launch { vm.events.collect { events += it } }
            advanceUntilIdle()

            vm.shareInChat(item, "conversation-1")
            advanceUntilIdle()
            collector.cancel()

            assertIs<VaultEvent.Error>(events.single())
        }

    /** A vault item that cannot be decrypted must not report as sent. */
    @Test
    fun `a failed download does not report a send`() =
        runTest(dispatcher) {
            coEvery { repo.listItems(any(), any()) } returns
                com.sanchr.domain.vault
                    .VaultPage(emptyList(), "")
            coEvery { repo.download(item) } throws IllegalStateException("bad key")

            val vm = viewModel()
            val events = mutableListOf<VaultEvent>()
            val collector = launch { vm.events.collect { events += it } }
            advanceUntilIdle()

            vm.shareInChat(item, "conversation-1")
            advanceUntilIdle()
            collector.cancel()

            assertIs<VaultEvent.Error>(events.single())
            coVerify(exactly = 0) { send(any(), any(), any(), any()) }
        }

    @Test
    fun `sharing outside emits the decrypted bytes for the system sheet`() =
        runTest(dispatcher) {
            coEvery { repo.listItems(any(), any()) } returns
                com.sanchr.domain.vault
                    .VaultPage(emptyList(), "")
            coEvery { repo.download(item) } returns byteArrayOf(9)

            val vm = viewModel()
            val events = mutableListOf<VaultEvent>()
            val collector = launch { vm.events.collect { events += it } }
            advanceUntilIdle()

            vm.shareOutside(item)
            advanceUntilIdle()
            collector.cancel()

            val event = assertIs<VaultEvent.ShareOutside>(events.single())
            assertEquals(item, event.item)
            assertEquals(listOf<Byte>(9), event.bytes.toList())
        }
}
