package com.sanchr.domain.contacts

import com.sanchr.core.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for contact operations.
 */
interface ContactRepository {
    /** Observes all contacts that are registered on Sanchr. */
    fun observeRegisteredContacts(): Flow<List<User>>

    /** Observes all contacts (including non-registered). */
    fun observeAllContacts(): Flow<List<User>>

    /** Searches contacts by name or phone number. */
    fun searchContacts(query: String): Flow<List<User>>

    /** Syncs device contacts with the server to discover registered users. */
    suspend fun syncContacts()

    /** Blocks or unblocks a contact. */
    suspend fun setBlocked(
        userId: String,
        blocked: Boolean,
    )

    /** Adds a contact to favorites. */
    suspend fun setFavorite(
        userId: String,
        favorite: Boolean,
    )

    /**
     * Looks up a single registered user by E.164 phone number.
     *
     * Returns `null` if the server has no registered user matching this phone
     * (NOT_FOUND). Non-NOT_FOUND errors (network, INVALID_ARGUMENT, etc.)
     * propagate as exceptions for the caller to handle.
     */
    suspend fun lookupByPhone(phoneE164: String): User?
}
