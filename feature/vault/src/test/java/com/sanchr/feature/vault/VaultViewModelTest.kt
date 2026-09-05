package com.sanchr.feature.vault

import com.sanchr.core.model.VaultItem
import com.sanchr.core.model.VaultItemType
import com.sanchr.domain.vault.VaultPage
import com.sanchr.domain.vault.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
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

    @Test
    fun `opening an item emits its decrypted bytes, and a failure an error`() =
        runTest(dispatcher) {
            val a = item("a", VaultItemType.DOCUMENT)
            coEvery { repo.listItems(any(), "") } returns VaultPage(listOf(a), "")
            coEvery { repo.download(a) } returns byteArrayOf(7, 7)
            val vm = VaultViewModel(repo)
            val events = mutableListOf<VaultEvent>()
            val eventJob = launch { vm.events.collect { events += it } }
            advanceUntilIdle()

            vm.openItem(a)
            advanceUntilIdle()
            val opened = events.single() as VaultEvent.Opened
            assertEquals("a", opened.item.id)
            assertTrue(byteArrayOf(7, 7).contentEquals(opened.bytes))

            coEvery { repo.download(a) } throws IllegalStateException("sealed")
            vm.openItem(a)
            advanceUntilIdle()
            assertEquals(VaultEvent.Error("Could not open item"), events.last())
            eventJob.cancel()
        }

    // --- Sorting: the six orders iOS offers, applied to the filtered list ---

    @Test
    fun `sorting orders the list and keeps the active filter`() =
        runTest(dispatcher) {
            fun photo(
                id: String,
                name: String,
                size: Long,
                created: Long,
            ) = VaultItem(id, "m-$id", VaultItemType.PHOTO, name, "image/jpeg", size, null, Instant.fromEpochMilliseconds(created))
            coEvery { repo.listItems(any(), any()) } returns
                VaultPage(
                    items =
                        listOf(
                            photo("a", "banana.jpg", size = 30, created = 100),
                            photo("b", "Apple.jpg", size = 10, created = 300),
                            photo("c", "cherry.jpg", size = 20, created = 200),
                        ),
                    nextCursor = "",
                )
            val vm = VaultViewModel(repo)
            val collector = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(
                "newest first by default",
                listOf("b", "c", "a"),
                vm.uiState.value.items
                    .map { it.id },
            )

            vm.setSort(VaultSort.OLDEST)
            advanceUntilIdle()
            assertEquals(
                listOf("a", "c", "b"),
                vm.uiState.value.items
                    .map { it.id },
            )

            // Case-insensitive, as iOS's localized standard compare: Apple before banana.
            vm.setSort(VaultSort.NAME_ASCENDING)
            advanceUntilIdle()
            assertEquals(
                listOf("Apple.jpg", "banana.jpg", "cherry.jpg"),
                vm.uiState.value.items
                    .map { it.name },
            )

            vm.setSort(VaultSort.NAME_DESCENDING)
            advanceUntilIdle()
            assertEquals(
                listOf("cherry.jpg", "banana.jpg", "Apple.jpg"),
                vm.uiState.value.items
                    .map { it.name },
            )

            vm.setSort(VaultSort.SIZE_LARGEST)
            advanceUntilIdle()
            assertEquals(
                listOf(30L, 20L, 10L),
                vm.uiState.value.items
                    .map { it.sizeBytes },
            )

            vm.setSort(VaultSort.SIZE_SMALLEST)
            advanceUntilIdle()
            assertEquals(
                listOf(10L, 20L, 30L),
                vm.uiState.value.items
                    .map { it.sizeBytes },
            )

            vm.setFilter(VaultFilter.FILES)
            advanceUntilIdle()
            assertEquals("a filter that matches nothing still yields nothing when sorted", emptyList<VaultItem>(), vm.uiState.value.items)
            assertEquals("the chosen order survives a filter change", VaultSort.SIZE_SMALLEST, vm.uiState.value.sort)
            collector.cancel()
        }

    // --- Multi-select: pick, select-all-visible, batch delete ---

    @Test
    fun `select mode picks items, select-all covers only the filtered view, and deleting clears the mode`() =
        runTest(dispatcher) {
            coEvery { repo.listItems(any(), any()) } returns
                VaultPage(listOf(item("a", VaultItemType.PHOTO), item("b", VaultItemType.PHOTO), item("d", VaultItemType.DOCUMENT)), "")
            coEvery { repo.deleteItem(any()) } returns Unit
            val vm = VaultViewModel(repo)
            val collector = launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.toggleSelection("a")
            advanceUntilIdle()
            assertFalse("tapping outside select mode does not select", vm.uiState.value.isSelectMode)

            vm.enterSelectMode()
            vm.toggleSelection("a")
            vm.toggleSelection("b")
            vm.toggleSelection("a")
            advanceUntilIdle()
            assertEquals(setOf("b"), vm.uiState.value.selectedIds)

            vm.setFilter(VaultFilter.PHOTOS)
            vm.selectAllVisible()
            advanceUntilIdle()
            assertEquals("select all takes the filtered view, not the whole vault", setOf("a", "b"), vm.uiState.value.selectedIds)

            vm.deleteSelected()
            advanceUntilIdle()
            coVerify { repo.deleteItem("a") }
            coVerify { repo.deleteItem("b") }
            assertFalse("the mode ends once the batch is done", vm.uiState.value.isSelectMode)

            vm.setFilter(VaultFilter.ALL)
            advanceUntilIdle()
            assertEquals(
                listOf("d"),
                vm.uiState.value.items
                    .map { it.id },
            )
            collector.cancel()
        }

    @Test
    fun `one failure does not stop the batch, and it is reported`() =
        runTest(dispatcher) {
            coEvery { repo.listItems(any(), any()) } returns
                VaultPage(listOf(item("a", VaultItemType.PHOTO), item("b", VaultItemType.PHOTO)), "")
            coEvery { repo.deleteItem("a") } throws IllegalStateException("server said no")
            coEvery { repo.deleteItem("b") } returns Unit
            val vm = VaultViewModel(repo)
            val collector = launch { vm.uiState.collect {} }
            val events = mutableListOf<VaultEvent>()
            val eventCollector = launch { vm.events.collect { events += it } }
            advanceUntilIdle()

            vm.enterSelectMode()
            vm.selectAllVisible()
            advanceUntilIdle()
            vm.deleteSelected()
            advanceUntilIdle()

            coVerify { repo.deleteItem("b") }
            assertEquals(
                "the one that failed is still there",
                listOf("a"),
                vm.uiState.value.items
                    .map { it.id },
            )
            assertTrue("the failure is reported", events.filterIsInstance<VaultEvent.Error>().isNotEmpty())
            collector.cancel()
            eventCollector.cancel()
        }
}
