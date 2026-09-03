package com.sanchr.app.data

import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.entity.ContactEntity
import com.sanchr.core.model.User
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.proto.contacts.BlockContactRequest
import com.sanchr.proto.contacts.ContactServiceClient
import com.sanchr.proto.contacts.MatchedContact
import com.sanchr.proto.contacts.SyncContactsRequest
import com.sanchr.proto.contacts.UnblockContactRequest
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

@Singleton
class ContactRepositoryImpl
    @Inject
    constructor(
        private val contactClient: ContactServiceClient,
        private val contactDao: ContactDao,
    ) : ContactRepository {
        override fun observeRegisteredContacts(): Flow<List<User>> =
            contactDao.observeRegisteredContacts().map { entities ->
                entities.map { it.toDomain() }
            }

        override fun observeAllContacts(): Flow<List<User>> =
            contactDao.observeContacts().map { entities ->
                entities.map { it.toDomain() }
            }

        override fun searchContacts(query: String): Flow<List<User>> =
            contactDao.searchContacts(query).map { entities ->
                entities.map { it.toDomain() }
            }

        override suspend fun syncContacts() {
            val response = contactClient.syncContacts(SyncContactsRequest())
            val now = System.currentTimeMillis()
            val entities =
                response.matchedContacts.map { matched ->
                    ContactEntity(
                        id = matched.userId,
                        userId = matched.userId,
                        phoneNumber = matched.phoneNumber,
                        displayName = matched.displayName,
                        avatarUrl = matched.avatarUrl.ifEmpty { null },
                        isRegistered = true,
                        lastSyncedAt = now,
                    )
                }
            if (entities.isNotEmpty()) {
                contactDao.insertContacts(entities)
            }
        }

        override suspend fun setBlocked(
            userId: String,
            blocked: Boolean,
        ) {
            contactDao.setBlocked(userId, blocked)
            if (blocked) {
                contactClient.blockContact(BlockContactRequest(userId = userId))
            } else {
                contactClient.unblockContact(UnblockContactRequest(userId = userId))
            }
        }

        override suspend fun setFavorite(
            userId: String,
            favorite: Boolean,
        ) {
            contactDao.setFavorite(userId, favorite)
        }

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
            val normalized = phoneE164.replace(Regex("[^0-9+]"), "")
            val hash =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(normalized.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
            val response =
                contactClient.syncContacts(
                    SyncContactsRequest(phoneHashes = listOf(hash)),
                )
            return response.matchedContacts.firstOrNull()?.toDomain(phoneE164)
        }

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
                displayName = displayName,
                avatarUrl = avatarUrl.ifEmpty { null },
                bio = null,
                isOnline = false,
                lastSeen = null,
                publicKeyFingerprint = null,
                createdAt = Instant.fromEpochMilliseconds(0L),
            )

        private fun ContactEntity.toDomain(): User =
            User(
                id = userId ?: id,
                phoneNumber = phoneNumber,
                displayName = displayName,
                avatarUrl = avatarUrl,
                bio = null,
                isOnline = false,
                lastSeen = null,
                publicKeyFingerprint = null,
                createdAt = Instant.fromEpochMilliseconds(lastSyncedAt ?: 0L),
            )
    }
