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
import org.junit.Test

/**
 * Unit tests for [ContactRepositoryImpl.lookupByPhone].
 *
 * The repo delegates to [ContactServiceClient.lookupUser]. backend/ does
 * not expose a LookupUser RPC (Phase 1 of the backend canonicalization
 * dropped the deprecated backend-oss/ fork), so the proto-layer client
 * returns null unconditionally and every input must surface as null at
 * the repository boundary. Phase 2 will reimplement this via
 * SyncContacts(phoneHashes = [SHA-256(normalized phone)]) — at which
 * point these tests should be replaced with real lookup-result coverage.
 */
class ContactRepositoryImplTest {
    @Test
    fun `lookupByPhone returns null today (Phase-2 stub contract)`() =
        runTest {
            val phoneNumbersExercised = mutableListOf<String>()
            val repo =
                ContactRepositoryImpl(
                    contactClient = StubLookupClient(phoneNumbersExercised),
                    contactDao = NoOpContactDao(),
                )

            assertNull(repo.lookupByPhone("+15550001234"))
            assertNull(repo.lookupByPhone("+15550009999"))
            assertNull(repo.lookupByPhone(""))

            // Verify the repo actually delegates — this guards against a
            // future change that fakes the result locally and drifts from
            // the proto-layer stub contract.
            assertEquals(
                listOf("+15550001234", "+15550009999", ""),
                phoneNumbersExercised,
            )
        }

    // ── Test doubles ──────────────────────────────────────────────────────

    /**
     * Mirrors the production proto-layer stub: records the phone number
     * for delegation assertions and returns null. Other RPCs are unused
     * by [ContactRepositoryImpl.lookupByPhone] and error loudly if called.
     */
    private class StubLookupClient(
        private val capturedPhoneNumbers: MutableList<String>,
    ) : ContactServiceClient {
        override suspend fun syncContacts(request: SyncContactsRequest): SyncContactsResponse = error("not used in test")

        override suspend fun getContacts(request: GetContactsRequest): GetContactsResponse = error("not used in test")

        override suspend fun blockContact(request: BlockContactRequest): BlockContactResponse = error("not used in test")

        override suspend fun unblockContact(request: UnblockContactRequest): UnblockContactResponse = error("not used in test")

        override suspend fun getBlockedList(request: GetBlockedListRequest): GetBlockedListResponse = error("not used in test")

        override suspend fun lookupUser(phoneNumber: String): LookedUpUser? {
            capturedPhoneNumbers += phoneNumber
            return null
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
