package com.sanchr.feature.vault

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.model.VaultItem
import com.sanchr.core.model.VaultItemType
import com.sanchr.domain.vault.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VaultFilter(
    val label: String,
) {
    ALL("All Media"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    FILES("Files"),
    ;

    fun accepts(item: VaultItem): Boolean =
        when (this) {
            ALL -> true
            PHOTOS -> item.type == VaultItemType.PHOTO
            VIDEOS -> item.type == VaultItemType.VIDEO
            FILES -> item.type != VaultItemType.PHOTO && item.type != VaultItemType.VIDEO
        }
}

data class VaultStats(
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val fileCount: Int = 0,
)

/** How the vault list is ordered, the same six orders iOS `VaultViewModel.SortOrder` offers. */
enum class VaultSort(
    val label: String,
) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    NAME_ASCENDING("Name (A → Z)"),
    NAME_DESCENDING("Name (Z → A)"),
    SIZE_LARGEST("Size (largest)"),
    SIZE_SMALLEST("Size (smallest)"),
    ;

    /** Applies this order. Names compare case-insensitively, as iOS's localized standard compare does. */
    fun sort(items: List<VaultItem>): List<VaultItem> =
        when (this) {
            NEWEST -> items.sortedByDescending { it.createdAt }
            OLDEST -> items.sortedBy { it.createdAt }
            NAME_ASCENDING -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            NAME_DESCENDING -> items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
            SIZE_LARGEST -> items.sortedByDescending { it.sizeBytes }
            SIZE_SMALLEST -> items.sortedBy { it.sizeBytes }
        }
}

data class VaultUiState(
    val items: List<VaultItem> = emptyList(),
    val filter: VaultFilter = VaultFilter.ALL,
    val sort: VaultSort = VaultSort.NEWEST,
    val stats: VaultStats = VaultStats(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isUploading: Boolean = false,
    val errorMessage: String? = null,
    val hasMore: Boolean = false,
)

/** A file the user picked, read on a background thread by the screen. */
class PickedFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
    /** A ≤ 48 KiB JPEG preview generated on the device, or null. */
    val thumbnailJpeg: ByteArray?,
)

@HiltViewModel
class VaultViewModel
    @Inject
    constructor(
        private val vaultRepository: VaultRepository,
    ) : ViewModel() {
        companion object {
            private const val TAG = "VaultViewModel"
        }

        private val _allItems = MutableStateFlow<List<VaultItem>>(emptyList())
        private val _filter = MutableStateFlow(VaultFilter.ALL)
        private val _isLoading = MutableStateFlow(true)
        private val _isRefreshing = MutableStateFlow(false)
        private val _sort = MutableStateFlow(VaultSort.NEWEST)
        private val _isUploading = MutableStateFlow(false)
        private val _errorMessage = MutableStateFlow<String?>(null)
        private val _nextCursor = MutableStateFlow("")
        private val _openingId = MutableStateFlow<String?>(null)
        val openingId: StateFlow<String?> = _openingId

        private val _events = MutableSharedFlow<VaultEvent>()
        val events = _events.asSharedFlow()

        val uiState: StateFlow<VaultUiState> =
            combine(
                combine(_allItems, _filter, _sort) { items, filter, sort -> Triple(items, filter, sort) },
                _isLoading,
                _isRefreshing,
                _isUploading,
            ) { (items, filter, sort), loading, refreshing, uploading ->
                VaultUiState(
                    items = sort.sort(items.filter(filter::accepts)),
                    filter = filter,
                    sort = sort,
                    stats =
                        VaultStats(
                            photoCount = items.count { it.type == VaultItemType.PHOTO },
                            videoCount = items.count { it.type == VaultItemType.VIDEO },
                            fileCount = items.count { VaultFilter.FILES.accepts(it) },
                        ),
                    isLoading = loading,
                    isRefreshing = refreshing,
                    isUploading = uploading,
                    errorMessage = _errorMessage.value,
                    hasMore = _nextCursor.value.isNotEmpty(),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = VaultUiState(),
            )

        init {
            loadPage()
        }

        fun setFilter(filter: VaultFilter) {
            _filter.value = filter
        }

        fun setSort(sort: VaultSort) {
            _sort.value = sort
        }

        fun refresh() {
            _isRefreshing.value = true
            _nextCursor.value = ""
            loadPage()
        }

        fun loadMore() {
            val cursor = _nextCursor.value
            if (cursor.isNotEmpty()) loadPage(cursor)
        }

        private fun loadPage(cursor: String = "") {
            viewModelScope.launch {
                try {
                    if (cursor.isEmpty()) _isLoading.value = _allItems.value.isEmpty()
                    _errorMessage.value = null
                    val page = vaultRepository.listItems(cursor = cursor)
                    _allItems.update { current -> if (cursor.isEmpty()) page.items else current + page.items }
                    _nextCursor.value = page.nextCursor
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load vault items", e)
                    if (_allItems.value.isEmpty()) _errorMessage.value = e.message ?: "Failed to load vault"
                } finally {
                    _isLoading.value = false
                    _isRefreshing.value = false
                }
            }
        }

        /** Encrypts and uploads [file]; the new item appears at the top on success. */
        fun addToVault(file: PickedFile) {
            if (_isUploading.value) return
            _isUploading.value = true
            viewModelScope.launch {
                try {
                    val item = vaultRepository.createItem(file.name, file.bytes, file.mimeType, file.thumbnailJpeg)
                    _allItems.update { listOf(item) + it }
                    _events.emit(VaultEvent.ItemAdded)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add to vault", e)
                    _events.emit(VaultEvent.Error("Upload failed"))
                } finally {
                    _isUploading.value = false
                }
            }
        }

        /** Downloads and decrypts [item]; the screen presents the bytes. */
        fun openItem(item: VaultItem) {
            if (_openingId.value != null) return
            _openingId.value = item.id
            viewModelScope.launch {
                try {
                    _events.emit(VaultEvent.Opened(item, vaultRepository.download(item)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open vault item", e)
                    _events.emit(VaultEvent.Error("Could not open item"))
                } finally {
                    _openingId.value = null
                }
            }
        }

        fun deleteItem(itemId: String) {
            viewModelScope.launch {
                try {
                    vaultRepository.deleteItem(itemId)
                    _allItems.update { items -> items.filter { it.id != itemId } }
                    _events.emit(VaultEvent.ItemDeleted)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete vault item", e)
                    _events.emit(VaultEvent.Error("Failed to delete item"))
                }
            }
        }
    }

sealed interface VaultEvent {
    data object ItemAdded : VaultEvent

    data object ItemDeleted : VaultEvent

    /** The decrypted payload of [item], ready to present. */
    class Opened(
        val item: VaultItem,
        val bytes: ByteArray,
    ) : VaultEvent

    data class Error(
        val message: String,
    ) : VaultEvent
}
