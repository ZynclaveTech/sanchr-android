package com.sanchr.feature.vault

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.proto.media.GetUploadUrlRequest
import com.sanchr.proto.media.MediaServiceClient
import com.sanchr.proto.vault.DeleteVaultItemRequest
import com.sanchr.proto.vault.GetVaultItemsRequest
import com.sanchr.proto.vault.ShareVaultItemRequest
import com.sanchr.proto.vault.VaultItem
import com.sanchr.proto.vault.VaultServiceClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    val category: String,
) {
    ALL("All Media", ""),
    PHOTOS("Photos", "photo"),
    VIDEOS("Videos", "video"),
    FILES("Files", "file"),
}

data class VaultStats(
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val fileCount: Int = 0,
)

data class VaultUiState(
    val items: List<VaultItem> = emptyList(),
    val filter: VaultFilter = VaultFilter.ALL,
    val stats: VaultStats = VaultStats(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val errorMessage: String? = null,
    val hasMore: Boolean = false,
)

@HiltViewModel
class VaultViewModel
    @Inject
    constructor(
        private val vaultServiceClient: VaultServiceClient,
        private val mediaServiceClient: MediaServiceClient,
    ) : ViewModel() {
        companion object {
            private const val TAG = "VaultViewModel"
            private const val PAGE_SIZE = 30
        }

        private val _allItems = MutableStateFlow<List<VaultItem>>(emptyList())
        private val _filter = MutableStateFlow(VaultFilter.ALL)
        private val _isLoading = MutableStateFlow(true)
        private val _isRefreshing = MutableStateFlow(false)
        private val _isUploading = MutableStateFlow(false)
        private val _uploadProgress = MutableStateFlow(0f)
        private val _errorMessage = MutableStateFlow<String?>(null)
        private val _nextPageToken = MutableStateFlow("")

        private val _events = MutableSharedFlow<VaultEvent>()
        val events = _events.asSharedFlow()

        val uiState: StateFlow<VaultUiState> =
            combine(
                _allItems,
                _filter,
                _isLoading,
                _isRefreshing,
                _isUploading,
            ) { items, filter, loading, refreshing, uploading ->
                val filtered =
                    when (filter) {
                        VaultFilter.ALL -> items
                        else -> items.filter { it.category.equals(filter.category, ignoreCase = true) }
                    }

                val stats =
                    VaultStats(
                        photoCount = items.count { it.category.equals("photo", ignoreCase = true) },
                        videoCount = items.count { it.category.equals("video", ignoreCase = true) },
                        fileCount = items.count { it.category.equals("file", ignoreCase = true) },
                    )

                VaultUiState(
                    items = filtered,
                    filter = filter,
                    stats = stats,
                    isLoading = loading,
                    isRefreshing = refreshing,
                    isUploading = uploading,
                    uploadProgress = _uploadProgress.value,
                    errorMessage = _errorMessage.value,
                    hasMore = _nextPageToken.value.isNotEmpty(),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = VaultUiState(),
            )

        init {
            loadVaultItems()
        }

        fun setFilter(filter: VaultFilter) {
            _filter.value = filter
        }

        fun refresh() {
            _isRefreshing.value = true
            _nextPageToken.value = ""
            loadVaultItems()
        }

        fun loadMore() {
            val token = _nextPageToken.value
            if (token.isNotEmpty()) {
                loadVaultItems(pageToken = token)
            }
        }

        private fun loadVaultItems(pageToken: String = "") {
            viewModelScope.launch {
                try {
                    if (pageToken.isEmpty()) {
                        _isLoading.value = _allItems.value.isEmpty()
                    }
                    _errorMessage.value = null

                    val response =
                        vaultServiceClient.getVaultItems(
                            GetVaultItemsRequest(
                                pageToken = pageToken,
                                pageSize = PAGE_SIZE,
                            ),
                        )

                    if (pageToken.isEmpty()) {
                        _allItems.value = response.items
                    } else {
                        _allItems.update { current -> current + response.items }
                    }
                    _nextPageToken.value = response.nextPageToken
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load vault items", e)
                    if (_allItems.value.isEmpty()) {
                        _errorMessage.value = e.message ?: "Failed to load vault"
                    }
                } finally {
                    _isLoading.value = false
                    _isRefreshing.value = false
                }
            }
        }

        fun deleteItem(itemId: String) {
            viewModelScope.launch {
                try {
                    val response =
                        vaultServiceClient.deleteVaultItem(
                            DeleteVaultItemRequest(itemId = itemId),
                        )
                    if (response.success) {
                        _allItems.update { items -> items.filter { it.id != itemId } }
                        _events.emit(VaultEvent.ItemDeleted)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete vault item", e)
                    _events.emit(VaultEvent.Error("Failed to delete item"))
                }
            }
        }

        fun shareItem(
            itemId: String,
            recipientUserIds: List<String>,
        ) {
            viewModelScope.launch {
                try {
                    val response =
                        vaultServiceClient.shareVaultItem(
                            ShareVaultItemRequest(
                                itemId = itemId,
                                recipientUserIds = recipientUserIds,
                            ),
                        )
                    if (response.success) {
                        _events.emit(VaultEvent.ItemShared(response.sharedCount))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to share vault item", e)
                    _events.emit(VaultEvent.Error("Failed to share item"))
                }
            }
        }

        fun requestUploadUrl(
            fileName: String,
            contentType: String,
            sizeBytes: Long,
        ) {
            viewModelScope.launch {
                _isUploading.value = true
                _uploadProgress.value = 0f
                try {
                    val presigned =
                        mediaServiceClient.getUploadUrl(
                            GetUploadUrlRequest(
                                fileName = fileName,
                                contentType = contentType,
                                sizeBytes = sizeBytes,
                                purpose = "vault",
                            ),
                        )
                    _uploadProgress.value = 0.3f
                    // Caller would use presigned.url to upload the actual file,
                    // then call confirmUpload. For now, emit the URL.
                    _events.emit(VaultEvent.UploadUrlReady(presigned.url, presigned.mediaId))
                    _uploadProgress.value = 1f
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get upload URL", e)
                    _events.emit(VaultEvent.Error("Upload failed: ${e.message}"))
                } finally {
                    _isUploading.value = false
                }
            }
        }
    }

sealed interface VaultEvent {
    data object ItemDeleted : VaultEvent

    data class ItemShared(
        val count: Int,
    ) : VaultEvent

    data class UploadUrlReady(
        val url: String,
        val mediaId: String,
    ) : VaultEvent

    data class Error(
        val message: String,
    ) : VaultEvent
}
