package com.sanchr.feature.vault

import com.sanchr.core.model.VaultItem
import com.sanchr.core.model.VaultItemType
import com.sanchr.domain.vault.VaultPage
import com.sanchr.domain.vault.VaultRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = mockk<VaultRepository>()

    private fun item(
        id: String,
        type: VaultItemType,
    ) = VaultItem(id, "m-$id", type, "$id.bin", "x/y", 10, null, Instant.fromEpochMilliseconds(1))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `first page loads, filters by type, and paging continues from the cursor`() =
        runTest(dispatcher) {
            coEvery { repo.listItems(any(), "") } returns
                VaultPage(listOf(item("a", VaultItemType.PHOTO), item("b", VaultItemType.DOCUMENT)), "c2")
            coEvery { repo.listItems(any(), "c2") } returns VaultPage(listOf(item("c", VaultItemType.VIDEO)), "")
            val vm = VaultViewModel(repo)
            val collector = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(
                listOf("a", "b"),
                vm.uiState.value.items
                    .map { it.id },
            )
            assertTrue(vm.uiState.value.hasMore)
            assertEquals(1, vm.uiState.value.stats.fileCount)

            vm.setFilter(VaultFilter.PHOTOS)
            advanceUntilIdle()
            assertEquals(
                listOf("a"),
                vm.uiState.value.items
                    .map { it.id },
            )

            vm.setFilter(VaultFilter.ALL)
            vm.loadMore()
            advanceUntilIdle()
            assertEquals(
                listOf("a", "b", "c"),
                vm.uiState.value.items
                    .map { it.id },
            )
            assertFalse(vm.uiState.value.hasMore)
            collector.cancel()
        }

    @Test
    fun `adding a file puts the new item first, and a failure reports an error`() =
        runTest(dispatcher) {
            coEvery { repo.listItems(any(), "") } returns VaultPage(listOf(item("a", VaultItemType.PHOTO)), "")
            coEvery { repo.createItem("new.jpg", any(), "image/jpeg", any()) } returns item("new", VaultItemType.PHOTO)
            val vm = VaultViewModel(repo)
            val collector = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            val events = mutableListOf<VaultEvent>()
            val eventJob = launch { vm.events.collect { events += it } }
            vm.addToVault(PickedFile("new.jpg", "image/jpeg", byteArrayOf(1), null))
            advanceUntilIdle()
            assertEquals(
                listOf("new", "a"),
                vm.uiState.value.items
                    .map { it.id },
            )
            assertEquals(VaultEvent.ItemAdded, events.single())
            assertFalse(vm.uiState.value.isUploading)

            coEvery { repo.createItem(any(), any(), any(), any()) } throws IllegalStateException("UNAVAILABLE")
            vm.addToVault(PickedFile("bad.pdf", "application/pdf", byteArrayOf(1), null))
            advanceUntilIdle()
            assertEquals(VaultEvent.Error("Upload failed"), events.last())
            assertEquals(
                listOf("new", "a"),
                vm.uiState.value.items
                    .map { it.id },
            )
            eventJob.cancel()
            collector.cancel()
        }

    @Test
    fun `deleting removes the item locally after the server accepts`() =
        runTest(dispatcher) {
            coEvery { repo.listItems(any(), "") } returns
                VaultPage(listOf(item("a", VaultItemType.PHOTO), item("b", VaultItemType.PHOTO)), "")
            coEvery { repo.deleteItem("a") } returns Unit
            val vm = VaultViewModel(repo)
            val collector = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.deleteItem("a")
            advanceUntilIdle()
            assertEquals(
                listOf("b"),
                vm.uiState
                    .first()
                    .items
                    .map { it.id },
            )
            collector.cancel()
        }
}
