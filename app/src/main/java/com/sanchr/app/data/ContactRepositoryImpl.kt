package com.sanchr.app.data

import android.util.Log
import com.sanchr.core.database.dao.BlockedUserDao
import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.dao.ContactProfileDao
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.entity.BlockedUserEntity
import com.sanchr.core.database.entity.ContactEntity
import com.sanchr.core.database.entity.ContactProfileEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.model.ContactDisplayName
import com.sanchr.core.model.User
import com.sanchr.core.notifications.NotificationHandler
import com.sanchr.domain.contacts.BlockedContact
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.domain.contacts.DeviceContact
import com.sanchr.domain.contacts.DiscoveryRepository
import com.sanchr.domain.contacts.PhoneNumberNormalizer
import com.sanchr.proto.contacts.BlockContactRequest
import com.sanchr.proto.contacts.Contact
import com.sanchr.proto.contacts.ContactServiceClient
import com.sanchr.proto.contacts.GetBlockedListRequest
import com.sanchr.proto.contacts.MatchedContact
import com.sanchr.proto.contacts.SyncContactsRequest
import com.sanchr.proto.contacts.UnblockContactRequest
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

@Singleton
class ContactRepositoryImpl
    @Inject
    constructor(
        private val contactClient: ContactServiceClient,
        private val contactDao: ContactDao,
        private val blockedUserDao: BlockedUserDao,
        private val conversationDao: ConversationDao,
        private val notificationHandler: NotificationHandler,
        private val userPreferences: UserPreferences,
        private val contactProfileDao: ContactProfileDao,
        private val discoveryRepository: DiscoveryRepository,
        private val sessionManager: SessionManager,
    ) : ContactRepository {
        override fun observeRegisteredContacts(): Flow<List<User>> = withProfiles(contactDao.observeRegisteredContacts())

        override fun observeAllContacts(): Flow<List<User>> = withProfiles(contactDao.observeContacts())

        override fun searchContacts(query: String): Flow<List<User>> = withProfiles(contactDao.searchContacts(query))

        /**
         * Joins each contact with the profile they published under their
         * Profile Key (if we have received it), so a name shows up as soon as
         * a first message from them has been decrypted — without a re-sync.
         */
        private fun withProfiles(contacts: Flow<List<ContactEntity>>): Flow<List<User>> =
            combine(contacts, contactProfileDao.observeAll()) { entities, profiles ->
                val byUserId = profiles.associateBy { it.userId }
                entities.map { it.toDomain(byUserId[it.userId ?: it.id]) }
            }

        override suspend fun syncContacts() {
            // Only numbers the server has already matched for this account:
            // the previously returned intersection, not the address book.
            val known = contactDao.getAllContacts().filter { it.isRegistered }.map { it.phoneNumber }
            if (known.isEmpty()) return
            // No address book in hand: names already on the rows are kept.
            resolveAndStore(known, addressBookNames = emptyMap())
        }

        override suspend fun discoverAndSyncContacts(deviceContacts: List<DeviceContact>): Int {
            val own = sessionManager.getStoredPhoneE164()
            // Every E.164 reading of a raw number maps back to the name the
            // user gave that entry; a number that appears under several
            // entries keeps the first non-blank name.
            val namesByCandidate = linkedMapOf<String, String>()
            for (contact in deviceContacts) {
                val name = contact.name?.trim().orEmpty()
                for (candidate in PhoneNumberNormalizer.candidates(contact.rawNumber, own)) {
                    if (name.isNotEmpty() && candidate !in namesByCandidate) namesByCandidate[candidate] = name
                    if (candidate !in namesByCandidate) namesByCandidate.putIfAbsent(candidate, "")
                }
            }
            val candidates = namesByCandidate.keys.toList()
            if (candidates.isEmpty()) return 0

            // OPRF-PSI: the server learns which blinded points it evaluated,
            // never which numbers. No fallback on failure — this throws.
            val registered = discoveryRepository.discoverRegistered(candidates)
            if (registered.isEmpty()) return 0

            return resolveAndStore(registered, addressBookNames = namesByCandidate.filterValues { it.isNotEmpty() })
        }

        /**
         * Resolves already-confirmed E.164 numbers to user records via
         * `SyncContacts` and upserts them. Callers must pass only numbers
         * discovery has confirmed (or that are already known to be
         * registered): this is the one call the server can read.
         */
        private suspend fun resolveAndStore(
            confirmedE164: List<String>,
            addressBookNames: Map<String, String>,
        ): Int {
            val response =
                contactClient.syncContacts(SyncContactsRequest(phoneHashes = confirmedE164.map { phoneHashHex(it) }))
            val now = System.currentTimeMillis()
            // REPLACE-on-insert would otherwise drop local state on a re-sync.
            // (Not blocks: those are in their own table, out of its reach.)
            val existing = contactDao.getAllContacts().associateBy { it.id }
            val entities =
                response.matchedContacts.map { matched ->
                    val previous = existing[matched.userId]
                    ContactEntity(
                        id = matched.userId,
                        userId = matched.userId,
                        phoneNumber = matched.phoneNumber,
                        // The address-book name — what *we* call them. The
                        // server's display_name is plaintext it should not
                        // have and is never stored; their own name reaches
                        // us encrypted, with their Profile Key.
                        displayName = addressBookNames[matched.phoneNumber] ?: previous?.displayName.orEmpty(),
                        avatarUrl = matched.avatarUrl.ifEmpty { null },
                        isRegistered = true,
                        isFavorite = previous?.isFavorite ?: false,
                        lastSyncedAt = now,
                    )
                }
            if (entities.isNotEmpty()) {
                contactDao.insertContacts(entities)
            }
            return entities.size
        }

        /**
         * The local write is authoritative; the server call is best-effort.
         *
         * The server enforces blocking keyed on the sender, which it cannot
         * read from a sealed envelope — so for 1:1 chats the block is applied
         * on this device by `ReceiveMessageUseCase` regardless of what the
         * server knows. Letting an RPC failure propagate would therefore fail
         * the *only* enforcement that actually works, which is backwards.
         *
         * A failed sync leaves the server unaware, which costs nothing for
         * sealed 1:1 traffic and self-corrects whenever the call next
         * succeeds.
         */
        override suspend fun setBlocked(
            userId: String,
            blocked: Boolean,
        ) {
            if (blocked) {
                blockedUserDao.block(BlockedUserEntity(userId = userId, blockedAt = System.currentTimeMillis()))
                // What they already sent may be sitting in the shade.
                conversationDao
                    .directConversationIdsWith("\"$userId\"")
                    .forEach(notificationHandler::cancelNotificationsForConversation)
            } else {
                blockedUserDao.unblock(userId)
            }
            // Told now if it can be, and remembered if not: the server's list
            // is what a new phone starts from.
            userPreferences.setPendingBlockSync(userId, if (tellServer(userId, blocked)) null else blocked)
        }

        override suspend fun syncPendingBlockChanges(): Int {
            var sent = 0
            for ((userId, blocked) in userPreferences.pendingBlockSync.first()) {
                if (tellServer(userId, blocked)) {
                    userPreferences.setPendingBlockSync(userId, null)
                    sent++
                }
            }
            return sent
        }

        /** @return whether the server took it. */
        private suspend fun tellServer(
            userId: String,
            blocked: Boolean,
        ): Boolean =
            try {
                if (blocked) {
                    contactClient.blockContact(BlockContactRequest(userId = userId))
                } else {
                    contactClient.unblockContact(UnblockContactRequest(userId = userId))
                }
                true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Log.w(TAG, "block state not synced to the server; enforced locally and kept to send again", error)
                false
            }

        override suspend fun setFavorite(
            userId: String,
            favorite: Boolean,
        ) {
            contactDao.setFavorite(userId, favorite)
        }

        override suspend fun isBlocked(userId: String): Boolean = blockedUserDao.isBlocked(userId)

        override suspend fun blockedContacts(): List<BlockedContact> {
            val blocked = blockedUserDao.blockedUserIds()
            if (blocked.isEmpty()) return emptyList()
            val serverNumbers = serverNumbersOrNone()
            return blocked.map { userId ->
                val local = contactDao.getContactById(userId)
                val profile = contactProfileDao.getByUserId(userId)
                val name =
                    ContactDisplayName
                        .resolve(
                            local?.displayName,
                            local?.phoneNumber?.ifBlank { null } ?: serverNumbers[userId],
                            profile?.displayName,
                        ).takeIf { it != ContactDisplayName.UNKNOWN }
                        ?: shortId(userId)
                BlockedContact(userId = userId, displayName = name)
            }
        }

        override suspend fun restoreBlockedFromServer(): Int {
            // Every page first: a list that fails half way is not written.
            val server = serverBlockedList()
            // Not anyone unblocked here whose unblock the server has yet to
            // hear: it still lists them, and taking its word would block
            // them again.
            val unblockedHere =
                userPreferences.pendingBlockSync
                    .first()
                    .filterValues { !it }
                    .keys
            val now = System.currentTimeMillis()
            blockedUserDao.blockAll(
                server.filterNot { it.userId in unblockedHere }.map { BlockedUserEntity(userId = it.userId, blockedAt = now) },
            )
            return server.size
        }

        /** The numbers the server holds for blocked people, or nothing at all if it cannot be asked. */
        private suspend fun serverNumbersOrNone(): Map<String, String> =
            try {
                serverBlockedList().filter { it.phoneNumber.isNotBlank() }.associate { it.userId to it.phoneNumber }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.w(TAG, "blocked list shown without the server's numbers", error)
                emptyMap()
            }

        private suspend fun serverBlockedList(): List<Contact> {
            val all = mutableListOf<Contact>()
            var pageToken = ""
            do {
                val page = contactClient.getBlockedList(GetBlockedListRequest(pageToken = pageToken, pageSize = BLOCKED_PAGE_SIZE))
                all += page.blockedUsers.filter { it.userId.isNotBlank() }
                pageToken = page.nextPageToken
            } while (pageToken.isNotEmpty())
            return all.distinctBy { it.userId }
        }

        private fun shortId(userId: String): String = if (userId.length > SHORT_ID_LENGTH) userId.take(SHORT_ID_LENGTH) + "..." else userId

        /**
         * Phone-based user lookup via SyncContacts(phoneHashes = [SHA-256(phone)]).
         *
         * Mirrors iOS ContactRepository.searchUser
         * (ios/Sanchr-iOS/Shared/Repositories/ContactRepository.swift:170-197):
         * the raw phone never leaves the device — only the SHA-256 of the
         * normalized E.164 form is sent. The server matches against its
         * registered-user hash table and returns at most one [MatchedContact]
         * for a single-element request.
         *
         * Hex-encoding the digest matches the existing convention in
         * `ContactsViewModel.readAndHashDeviceContacts`, which is the only
         * other Android caller of `SyncContacts`. Both clients (Android
         * single-element here, Android bulk in ContactsViewModel) must agree
         * on encoding so the server returns matches consistently.
         *
         * Returns null when the server reports no match. Network / RPC errors
         * propagate to the caller (matching the iOS contract — the VM layer
         * decides what to surface to the UI).
         */
        override suspend fun lookupByPhone(phoneE164: String): User? {
            val candidates = PhoneNumberNormalizer.candidates(phoneE164, sessionManager.getStoredPhoneE164())
            if (candidates.isEmpty()) return null

            // OPRF first, exactly as the address-book sync: manual entry must
            // not be a path around discovery's privacy. Only a confirmed
            // number is ever hashed and sent to SyncContacts.
            val matched = discoveryRepository.discoverRegistered(candidates).firstOrNull() ?: return null
            val response = contactClient.syncContacts(SyncContactsRequest(phoneHashes = listOf(phoneHashHex(matched))))
            return response.matchedContacts.firstOrNull()?.toDomain(matched)
        }

        /** Unsalted SHA-256 of the E.164 UTF-8 bytes, lowercase hex — the same digest iOS's `hashPhoneNumber` sends. */
        private fun phoneHashHex(e164: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(e164.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        // ── Mapping helpers ──

        /**
         * Maps a single-result [MatchedContact] (from the phone-lookup path)
         * into the domain [User]. The caller's `phoneE164` is preferred over
         * the server-reported `phoneNumber` because (a) the lookup-by-hash RPC
         * may legitimately omit phone-number echo (privacy), and (b) the
         * caller already knows the canonical E.164 form they searched for.
         * Server-supplied phone is used only as a fallback.
         */
        private fun MatchedContact.toDomain(phoneE164: String): User =
            User(
                id = userId,
                phoneNumber = phoneNumber.ifEmpty { phoneE164 },
                // The server's display_name is plaintext it should not have
                // (a placeholder, or a name uploaded before profiles were
                // encrypted) and is never shown; a lookup by number knows the
                // number, and the profile arrives with their first message.
                displayName =
                    ContactDisplayName.resolve(
                        addressBookName = null,
                        phoneNumber = phoneNumber.ifEmpty { phoneE164 },
                        profileName = null,
                    ),
                avatarUrl = avatarUrl.ifEmpty { null },
                bio = null,
                isOnline = false,
                lastSeen = null,
                publicKeyFingerprint = null,
                createdAt = Instant.fromEpochMilliseconds(0L),
            )

        private fun ContactEntity.toDomain(profile: ContactProfileEntity?): User =
            User(
                id = userId ?: id,
                phoneNumber = phoneNumber,
                displayName = ContactDisplayName.resolve(displayName, phoneNumber, profile?.displayName),
                avatarUrl = profile?.avatarUrl ?: avatarUrl,
                bio = profile?.bio,
                isOnline = false,
                lastSeen = null,
                publicKeyFingerprint = null,
                createdAt = Instant.fromEpochMilliseconds(lastSyncedAt ?: 0L),
            )

        private companion object {
            const val BLOCKED_PAGE_SIZE = 50
            const val SHORT_ID_LENGTH = 12
            private const val TAG = "ContactRepository"
        }
    }
