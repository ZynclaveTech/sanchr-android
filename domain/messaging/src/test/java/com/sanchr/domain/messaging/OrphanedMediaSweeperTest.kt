package com.sanchr.domain.messaging

import com.sanchr.domain.messaging.media.AttachmentCacheNames
import com.sanchr.domain.messaging.media.AttachmentDownloader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/** A cached photo whose message is gone is removed; anything it is unsure of is left. */
class OrphanedMediaSweeperTest {
    private val repository = mockk<MessageRepository>(relaxed = true)
    private val downloader = mockk<AttachmentDownloader>(relaxed = true)
    private val sweeper = OrphanedMediaSweeper(repository, downloader)
    private val now = 10_000_000_000L
    private val old = now - OrphanedMediaSweeper.MIN_AGE_MILLIS

    private val kept = "0f8fad5b-d9cb-469f-a165-70867728950e"
    private val gone = "7c9e6679-7425-40de-944b-e07fc1f90ae7"

    private fun file(
        name: String,
        modified: Long = old,
    ) = mockk<File> {
        every { this@mockk.name } returns name
        every { lastModified() } returns modified
    }

    private fun cacheHolds(vararg files: File) {
        coEvery { downloader.cachedFiles() } returns files.toList()
        coEvery { downloader.remove(any()) } returns true
        coEvery { repository.messageIdsHoldingMedia(any()) } returns setOf(kept)
    }

    @Test
    fun `a file whose message is gone is removed, and one whose message is here is not`() =
        runTest {
            val orphan = file("$gone.jpg")
            val live = file("$kept.jpg")
            cacheHolds(orphan, live)

            assertEquals(1, sweeper.sweep(now))

            coVerify(exactly = 1) { downloader.remove(orphan) }
            coVerify(exactly = 0) { downloader.remove(live) }
        }

    @Test
    fun `album tiles and half-finished downloads go with their message`() =
        runTest {
            val tile = file("$gone#2.mp4")
            val partial = file("$gone.jpg.part")
            val liveTile = file("$kept#0.jpg")
            cacheHolds(tile, partial, liveTile)

            assertEquals(2, sweeper.sweep(now))

            coVerify(exactly = 0) { downloader.remove(liveTile) }
        }

    /** Something being sent or saved right now may not have its row, or its final id, yet. */
    @Test
    fun `a recent file is left alone whatever the database says`() =
        runTest {
            val fresh = file("$gone.jpg", modified = old + 1)
            cacheHolds(fresh)

            assertEquals(0, sweeper.sweep(now))

            coVerify(exactly = 0) { downloader.remove(any()) }
        }

    @Test
    fun `a file whose name does not give a message id is left alone`() =
        runTest {
            val hashed = file("a".repeat(64) + ".jpg")
            val odd = file("thumbs.db")
            cacheHolds(hashed, odd)

            assertEquals(0, sweeper.sweep(now))

            coVerify(exactly = 0) { downloader.remove(any()) }
            coVerify(exactly = 0) { repository.messageIdsHoldingMedia(any()) }
        }

    @Test
    fun `a database that cannot answer removes nothing`() =
        runTest {
            cacheHolds(file("$gone.jpg"))
            coEvery { repository.messageIdsHoldingMedia(any()) } throws RuntimeException("db")

            assertEquals(0, sweeper.sweep(now))

            coVerify(exactly = 0) { downloader.remove(any()) }
        }

    @Test
    fun `a file name is read back to the message it was cached for`() {
        assertEquals(kept, AttachmentCacheNames.messageIdOf("$kept.jpg"))
        assertEquals(kept, AttachmentCacheNames.messageIdOf("$kept#3.mp4"))
        assertEquals(kept, AttachmentCacheNames.messageIdOf("$kept.jpg.part"))
        // No extension: a type Android has no extension for.
        assertEquals(kept, AttachmentCacheNames.messageIdOf(kept))
        assertNull(AttachmentCacheNames.messageIdOf("a".repeat(64) + ".jpg"))
        assertNull(AttachmentCacheNames.messageIdOf(".nomedia"))
        assertNull(AttachmentCacheNames.messageIdOf("not an id.jpg"))
    }

    @Test
    fun `reading a name back agrees with how the name was made`() {
        for (id in listOf(kept, "$kept#0", "$kept#12")) {
            assertEquals(kept, AttachmentCacheNames.messageIdOf(AttachmentCacheNames.fileName(id) + ".jpg"))
        }
    }
}
