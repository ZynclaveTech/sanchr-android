package com.sanchr.sync.vault

import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.model.ChatVaultPolicy
import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.core.model.MessageStatus
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.media.AttachmentDownloader
import com.sanchr.domain.messaging.media.AttachmentException
import com.sanchr.domain.vault.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/**
 * Moving a received message into the vault. What matters is the order: the
 * media leaves the chat only after all of it is in the vault, and a failure
 * anywhere before that leaves the message as it arrived.
 */
class AutoVaultIncomingTest {
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val downloader = mockk<AttachmentDownloader>(relaxed = true)
    private val vault = mockk<VaultRepository>(relaxed = true)
    private val session = mockk<SessionManager> { every { getUserId() } returns "self" }
    private val autoVault = AutoVaultIncoming(messageRepository, session, downloader, vault)
    private val temp = mutableListOf<File>()

    @AfterTest
    fun cleanUp() = temp.forEach { it.delete() }

    private fun file(bytes: ByteArray = byteArrayOf(1, 2, 3)): File =
        File
            .createTempFile("vault", ".bin")
            .also {
                it.writeBytes(bytes)
            }.also(temp::add)

    private fun attachment(name: String) = MediaAttachment(url = "sanchr-media://$name", mimeType = "application/pdf", filename = name)

    private fun stored(
        content: MessageContent,
        policy: ChatVaultPolicy = ChatVaultPolicy(autoVaultIncoming = true),
    ) {
        coEvery { messageRepository.message("m1") } returns
            Message(
                id = "m1",
                conversationId = "c1",
                senderId = "peer",
                content = content,
                status = MessageStatus.DELIVERED,
                timestamp = Instant.fromEpochMilliseconds(1),
            )
        coEvery { messageRepository.vaultPolicy("c1") } returns policy
    }

    private fun fileMessage(name: String = "d.pdf") =
        MessageContent.File(url = "u", fileName = name, mimeType = "application/pdf", sizeBytes = 3, attachment = attachment(name))

    @Test
    fun `the media is vaulted, then the chat row is replaced, then the cache is cleared`() =
        runTest {
            stored(fileMessage())
            coEvery { downloader.open("m1", any()) } returns file()

            assertTrue(autoVault("m1"))

            coVerifyOrder {
                vault.createItem("d.pdf", any(), "application/pdf", null)
                messageRepository.tombstoneAutoVaulted("m1")
                downloader.evict("m1", "application/pdf")
            }
        }

    @Test
    fun `a switch turned off since the work was scheduled is honoured`() =
        runTest {
            stored(fileMessage(), policy = ChatVaultPolicy.DEFAULT)

            assertFalse(autoVault("m1"))

            coVerify(exactly = 0) { downloader.open(any(), any()) }
            coVerify(exactly = 0) { messageRepository.tombstoneAutoVaulted(any()) }
        }

    @Test
    fun `a message deleted since the work was scheduled is nothing to do`() =
        runTest {
            coEvery { messageRepository.message("m1") } returns null

            assertFalse(autoVault("m1"))

            coVerify(exactly = 0) { vault.createItem(any(), any(), any(), any()) }
        }

    @Test
    fun `a failed vault upload leaves the message in the chat`() =
        runTest {
            stored(fileMessage())
            coEvery { downloader.open("m1", any()) } returns file()
            coEvery { vault.createItem(any(), any(), any(), any()) } throws IOException("offline")

            assertFailsWith<IOException> { autoVault("m1") }

            coVerify(exactly = 0) { messageRepository.tombstoneAutoVaulted(any()) }
            coVerify(exactly = 0) { downloader.evict(any(), any()) }
        }

    @Test
    fun `an album tile that cannot be downloaded stops before anything is vaulted`() =
        runTest {
            stored(MessageContent.Album(listOf(attachment("a"), attachment("b"))))
            coEvery { downloader.open("m1#0", any()) } returns file()
            coEvery { downloader.open("m1#1", any()) } throws AttachmentException("gone")

            assertFailsWith<AttachmentException> { autoVault("m1") }

            // Not half an album in the vault and the whole of it still in the chat.
            coVerify(exactly = 0) { vault.createItem(any(), any(), any(), any()) }
            coVerify(exactly = 0) { messageRepository.tombstoneAutoVaulted(any()) }
        }

    @Test
    fun `every tile of an album is vaulted and evicted under its own cache id`() =
        runTest {
            stored(MessageContent.Album(listOf(attachment("a"), attachment("b"))))
            coEvery { downloader.open(any(), any()) } returns file()

            assertTrue(autoVault("m1"))

            coVerify(exactly = 1) { vault.createItem("a", any(), any(), any()) }
            coVerify(exactly = 1) { vault.createItem("b", any(), any(), any()) }
            coVerify(exactly = 1) { downloader.evict("m1#0", any()) }
            coVerify(exactly = 1) { downloader.evict("m1#1", any()) }
        }
}
