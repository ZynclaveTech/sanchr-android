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

    /**
     * Re-resolves the contacts already known to be registered, refreshing
     * their profiles. Sends hashes of numbers the server has *already*
     * matched for this account — the bounded set discovery previously
     * returned — never the address book. Safe to call in the background
     * without contacts permission.
     */
    suspend fun syncContacts()

    /**
     * Full privacy-preserving discovery over the device address book.
     *
     * Normalises each [DeviceContact.rawNumber] to E.164 using the signed-in
     * user's own country, asks [DiscoveryRepository] which are registered
     * (the server sees blinded points only), and resolves **just that
     * intersection** to user records via `SyncContacts`. The server
     * necessarily learns the intersection — it has to, to return the
     * accounts — but never the address book. The address-book name is kept
     * locally as what we call the contact; the server's `display_name` is
     * never stored.
     *
     * Fails closed: a discovery error propagates. There is deliberately no
     * fallback to uploading hashes of everything, which is the leak this
     * exists to prevent.
     *
     * @return the number of registered contacts found.
     */
    suspend fun discoverAndSyncContacts(deviceContacts: List<DeviceContact>): Int

    /** Blocks or unblocks a contact. */
    suspend fun setBlocked(
        userId: String,
        blocked: Boolean,
    )

    /** Whether we have blocked [userId], from this phone's own record. */
    suspend fun isBlocked(userId: String): Boolean

    /**
     * Everyone this phone has blocked, most recent first, each named by our
     * own resolution of the person, falling back to the number the server
     * knows and finally a shortened id, as iOS `BlockedContactsView`.
     *
     * The phone's record, not the server's: it is the one that is enforced,
     * so it is the one to show. The server is asked only for the numbers of
     * people we cannot otherwise name, and the list does not need it.
     */
    suspend fun blockedContacts(): List<BlockedContact>

    /**
     * Adds everyone on the server's blocked list to this phone's.
     *
     * Blocks are enforced from the phone's record, which starts empty on a
     * new install. Adds only — it never unblocks. Throws if the server
     * cannot be reached, having changed nothing.
     *
     * @return how many people the server's list held.
     */
    suspend fun restoreBlockedFromServer(): Int

    /**
     * Sends the server every block and unblock it has not yet been told
     * about. One that fails again is kept for the next time.
     *
     * @return how many were sent.
     */
    suspend fun syncPendingBlockChanges(): Int

    /** Adds a contact to favorites. */
    suspend fun setFavorite(
        userId: String,
        favorite: Boolean,
    )

    /**
     * Looks up a single registered user by E.164 phone number.
     *
     * Returns `null` if no registered user matches this phone. Asks the same
     * way the address-book sync does — OPRF first, then resolve only on a
     * match — so manual entry is not a path around discovery's privacy. Other
     * errors (network, protocol) propagate for the caller to handle.
     */
    suspend fun lookupByPhone(phoneE164: String): User?
}
