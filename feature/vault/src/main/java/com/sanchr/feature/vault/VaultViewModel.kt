package com.sanchr.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanchr.core.model.VaultItem
import com.sanchr.domain.vault.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaultUiState(
    val items: List<VaultItem> = emptyList(),
    val isLoading: Boolean = true,
    val storageUsedBytes: Long = 0L,
    val errorMessage: String? = null,
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    val uiState: StateFlow<VaultUiState> = vaultRepository.observeVaultItems()
        .map { items ->
            VaultUiState(
                items = items,
                isLoading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = VaultUiState(),
        )

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            try {
                vaultRepository.deleteItem(itemId)
            } catch (e: Exception) {
                // TODO: Propagate error to UI state
            }
        }
    }
}
