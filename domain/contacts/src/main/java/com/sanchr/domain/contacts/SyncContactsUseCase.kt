package com.sanchr.domain.contacts

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.core.common.runCatchingResult
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Syncs device contacts with the Sanchr backend.
 * Hashes phone numbers before upload for privacy.
 */
class SyncContactsUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend operator fun invoke(): Result<Unit> = withContext(dispatcherProvider.io) {
        runCatchingResult {
            contactRepository.syncContacts()
        }
    }
}
