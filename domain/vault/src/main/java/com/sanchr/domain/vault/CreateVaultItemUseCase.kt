package com.sanchr.domain.vault

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.core.common.runCatchingResult
import com.sanchr.core.model.VaultItem
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Creates a new item in the encrypted vault.
 * Encrypts the data with AES-GCM before storing.
 */
class CreateVaultItemUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend operator fun invoke(
        name: String,
        data: ByteArray,
        mimeType: String,
    ): Result<VaultItem> = withContext(dispatcherProvider.io) {
        runCatchingResult {
            require(name.isNotBlank()) { "Vault item name must not be blank" }
            require(data.isNotEmpty()) { "Vault item data must not be empty" }
            vaultRepository.createItem(name, data, mimeType)
        }
    }
}
