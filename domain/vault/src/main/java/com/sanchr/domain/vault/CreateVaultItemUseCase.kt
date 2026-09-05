package com.sanchr.domain.vault

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.core.common.runCatchingResult
import com.sanchr.core.model.VaultItem
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** Adds an item to the encrypted vault. */
class CreateVaultItemUseCase
    @Inject
    constructor(
        private val vaultRepository: VaultRepository,
        private val dispatcherProvider: DispatcherProvider,
    ) {
        suspend operator fun invoke(
            name: String,
            data: ByteArray,
            mimeType: String,
            thumbnailJpeg: ByteArray? = null,
        ): Result<VaultItem> =
            withContext(dispatcherProvider.io) {
                runCatchingResult {
                    require(name.isNotBlank()) { "Vault item name must not be blank" }
                    require(data.isNotEmpty()) { "Vault item data must not be empty" }
                    vaultRepository.createItem(name, data, mimeType, thumbnailJpeg)
                }
            }
    }
