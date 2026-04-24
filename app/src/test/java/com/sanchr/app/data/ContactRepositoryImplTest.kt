package com.sanchr.app.data

import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.entity.ContactEntity
import com.sanchr.proto.contacts.BlockContactRequest
import com.sanchr.proto.contacts.BlockContactResponse
import com.sanchr.proto.contacts.ContactServiceClient
import com.sanchr.proto.contacts.GetBlockedListRequest
import com.sanchr.proto.contacts.GetBlockedListResponse
import com.sanchr.proto.contacts.GetContactsRequest
import com.sanchr.proto.contacts.GetContactsResponse
import com.sanchr.proto.contacts.LookedUpUser
import com.sanchr.proto.contacts.SyncContactsRequest
import com.sanchr.proto.contacts.SyncContactsResponse
import com.sanchr.proto.contacts.UnblockContactRequest
import com.sanchr.proto.contacts.UnblockContactResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Unit tests for [ContactRepositoryImpl.lookupByPhone].
 *
 * The repo delegates to [ContactServiceClient.lookupUser], which surfaces
 * server-side NOT_FOUND as a null return. Anything else (network failure,
 * INVALID_ARGUMENT, etc.) must propagate so callers can react — the repo
 * must NOT swallow those.
 */
class ContactRepositoryImplTest {
    private val sampleUser =
        LookedUpUser(
            id = "11111111-2222-3333-4444-555555555555",
            phoneNumber = "+15550001234",
            displayName = "Alice",
            email = "",
            avatarUrl = "https://cdn.example/a.png",
            statusText = "",
            createdAt = "2024-01-02T03:04:05Z",
        )

    @Test
    fun `lookupByPhone returns mapped domain user on success`() =
        runTest {
            val repo =
                ContactRepositoryImpl(
                    contactClient = FakeContactServiceClient(result = LookupResult.Found(sampleUser)),
                    contactDao = NoOpContactDao(),
                )

            val user = repo.lookupByPhone("+15550001234")

            assertEquals(sampleUser.id, user?.id)
            assertEquals(sampleUser.phoneNumber, user?.phoneNumber)
            assertEquals(sampleUser.displayName, user?.displayName)
            assertEquals(sampleUser.avatarUrl, user?.avatarUrl)
        }

    @Test
    fun `lookupByPhone returns null when client reports NOT_FOUND`() =
        runTest {
            val repo =
                ContactRepositoryImpl(
                    contactClient = FakeContactServiceClient(result = LookupResult.NotFound),
                    contactDao = NoOpContactDao(),
                )

            val user = repo.lookupByPhone("+15550009999")

            assertNull(user)
        }

    @Test
    fun `lookupByPhone propagates non-NOT_FOUND errors`() =
        runTest {
            val boom = IllegalStateException("network down")
            val repo =
                ContactRepositoryImpl(
                    contactClient = FakeContactServiceClient(result = LookupResult.Throws(boom)),
                    contactDao = NoOpContactDao(),
                )

            val thrown =
                assertThrows(IllegalStateException::class.java) {
                    kotlinx.coroutines.runBlocking { repo.lookupByPhone("+15550001234") }
                }
            assertEquals("network down", thrown.message)
        }

    // ── Test doubles ──────────────────────────────────────────────────────

    private sealed interface LookupResult {
        data class Found(
            val user: LookedUpUser,
        ) : LookupResult

        object NotFound : LookupResult

        data class Throws(
            val error: Throwable,
        ) : LookupResult
    }

    private class FakeContactServiceClient(
        private val result: LookupResult,
    ) : ContactServiceClient {
        override suspend fun syncContacts(request: SyncContactsRequest): SyncContactsResponse = error("not used in test")

        override suspend fun getContacts(request: GetContactsRequest): GetContactsResponse = error("not used in test")

        override suspend fun blockContact(request: BlockContactRequest): BlockContactResponse = error("not used in test")

        override suspend fun unblockContact(request: UnblockContactRequest): UnblockContactResponse = error("not used in test")

        override suspend fun getBlockedList(request: GetBlockedListRequest): GetBlockedListResponse = error("not used in test")

        override suspend fun lookupUser(phoneNumber: String): LookedUpUser? =
            when (val r = result) {
                is LookupResult.Found -> r.user
                LookupResult.NotFound -> null
                is LookupResult.Throws -> throw r.error
            }
    }

    /**
     * ContactDao is only referenced for the other repo methods. lookupByPhone
     * never touches it, so every method here errors loudly if called.
     */
    private class NoOpContactDao : ContactDao {
        override fun observeContacts(): Flow<List<ContactEntity>> = emptyFlow()

        override fun observeRegisteredContacts(): Flow<List<ContactEntity>> = emptyFlow()

        override fun observeFavoriteContacts(): Flow<List<ContactEntity>> = emptyFlow()

        override suspend fun getContactById(contactId: String): ContactEntity? = null

        override suspend fun getContactByPhoneNumber(phoneNumber: String): ContactEntity? = null

        override suspend fun getAllContacts(): List<ContactEntity> = emptyList()

        override fun searchContacts(query: String): Flow<List<ContactEntity>> = emptyFlow()

        override suspend fun insertContacts(contacts: List<ContactEntity>) = Unit

        override suspend fun updateContact(contact: ContactEntity) = Unit

        override suspend fun setBlocked(
            contactId: String,
            isBlocked: Boolean,
        ) = Unit

        override suspend fun setFavorite(
            contactId: String,
            isFavorite: Boolean,
        ) = Unit

        override suspend fun deleteContact(contactId: String) = Unit

        override suspend fun deleteAllContacts() = Unit
    }
}
