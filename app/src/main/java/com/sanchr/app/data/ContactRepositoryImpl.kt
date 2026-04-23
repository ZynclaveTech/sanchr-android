package com.sanchr.app.data

import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.entity.ContactEntity
import com.sanchr.core.model.User
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.proto.contacts.BlockContactRequest
import com.sanchr.proto.contacts.ContactServiceClient
import com.sanchr.proto.contacts.SyncContactsRequest
import com.sanchr.proto.contacts.UnblockContactRequest
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

        // ── Mapping helpers ──

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
