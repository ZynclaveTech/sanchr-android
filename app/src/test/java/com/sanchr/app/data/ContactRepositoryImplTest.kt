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
import com.sanchr.proto.contacts.MatchedContact
import com.sanchr.proto.contacts.SyncContactsRequest
import com.sanchr.proto.contacts.SyncContactsResponse
import com.sanchr.proto.contacts.UnblockContactRequest
import com.sanchr.proto.contacts.UnblockContactResponse
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [ContactRepositoryImpl.lookupByPhone].
 *
 * The repo hashes the normalized E.164 phone number with SHA-256 (hex
 * encoding, matching ContactsViewModel.readAndHashDeviceContacts) and calls
 * SyncContacts with a single-element phone_hashes list, mirroring iOS
 * ContactRepository.searchUser
 * (ios/Sanchr-iOS/Shared/Repositories/ContactRepository.swift:170-197).
 *
 * Coverage:
 *  - the request hash matches the SHA-256 hex of the normalized phone
 *  - a single server match maps into the domain User (preferring caller's
 *    canonical E.164 over the server-echoed phone, which the lookup RPC may
 *    omit for privacy)
 *  - empty matches yield null (NOT_FOUND parity)
 *  - RPC exceptions propagate (network / INVALID_ARGUMENT etc.)
 */
class ContactRepositoryImplTest {
    @Test
    fun `lookupByPhone normalizes and hashes the phone before calling SyncContacts`() =
        runTest {
            val client = RecordingSyncContactsClient(response = SyncContactsResponse())
            val repo =
                ContactRepositoryImpl(
                    contactClient = client,
                    contactDao = NoOpContactDao(),
                )

            // Note the punctuation: spaces, parens, and dashes must be stripped
            // by the normalizer; only digits and the leading '+' survive.
            repo.lookupByPhone("+1 (555) 000-1234")

            val expectedHash =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest("+15550001234".toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }

            val captured = client.capturedRequests.single()
            assertEquals(listOf(expectedHash), captured.phoneHashes)
        }

    @Test
    fun `lookupByPhone with single server match returns mapped User`() =
        runTest {
            val client =
                RecordingSyncContactsClient(
                    response =
                        SyncContactsResponse(
                            matchedContacts =
                                listOf(
                                    MatchedContact(
                                        userId = "user-42",
                                        displayName = "Ada Lovelace",
                                        avatarUrl = "https://cdn/avatar.png",
                                        phoneNumber = "",
                                    ),
                                ),
                        ),
                )
            val repo =
                ContactRepositoryImpl(
                    contactClient = client,
                    contactDao = NoOpContactDao(),
                )

            val user = repo.lookupByPhone("+15550001234")
            assertNotNull(user)
            requireNotNull(user)
            assertEquals("user-42", user.id)
            assertEquals("Ada Lovelace", user.displayName)
            assertEquals("https://cdn/avatar.png", user.avatarUrl)
            // Server omitted phone_number (privacy on the lookup path) — the
            // repo falls back to the caller's E.164.
            assertEquals("+15550001234", user.phoneNumber)
        }

    @Test
    fun `lookupByPhone adopts non-empty server phone over caller E164`() =
        runTest {
            val client =
                RecordingSyncContactsClient(
                    response =
                        SyncContactsResponse(
                            matchedContacts =
                                listOf(
                                    MatchedContact(
                                        userId = "user-7",
                                        displayName = "Grace",
                                        phoneNumber = "+15559999999",
                                    ),
                                ),
                        ),
                )
            val repo =
                ContactRepositoryImpl(
                    contactClient = client,
                    contactDao = NoOpContactDao(),
                )

            val user = repo.lookupByPhone("+15550001234")
            // When the server does echo a phone, that authoritative copy wins
            // (e.g. the server may have re-normalized to E.164 differently).
            assertEquals("+15559999999", user?.phoneNumber)
        }

    @Test
    fun `lookupByPhone with no matches returns null`() =
        runTest {
            val client = RecordingSyncContactsClient(response = SyncContactsResponse())
            val repo =
                ContactRepositoryImpl(
                    contactClient = client,
                    contactDao = NoOpContactDao(),
                )

            assertNull(repo.lookupByPhone("+15550009999"))
        }

    @Test
    fun `lookupByPhone propagates RPC failure to caller`() =
        runTest {
            val boom = RuntimeException("network down")
            val repo =
                ContactRepositoryImpl(
                    contactClient = ThrowingClient(boom),
                    contactDao = NoOpContactDao(),
                )

            try {
                repo.lookupByPhone("+15550001234")
                fail("expected RuntimeException to bubble out of lookupByPhone")
            } catch (e: RuntimeException) {
                assertSame(boom, e)
            }
        }

    // ── Test doubles ──────────────────────────────────────────────────────

    /**
     * Captures every SyncContacts request and returns a canned response.
     * Other RPCs throw if the implementation drifts and starts calling them.
     */
    private class RecordingSyncContactsClient(
        private val response: SyncContactsResponse,
    ) : ContactServiceClient {
        val capturedRequests = mutableListOf<SyncContactsRequest>()

        override suspend fun syncContacts(request: SyncContactsRequest): SyncContactsResponse {
            capturedRequests += request
            return response
        }

        override suspend fun getContacts(request: GetContactsRequest): GetContactsResponse = error("not used in test")

        override suspend fun blockContact(request: BlockContactRequest): BlockContactResponse = error("not used in test")

        override suspend fun unblockContact(request: UnblockContactRequest): UnblockContactResponse = error("not used in test")

        override suspend fun getBlockedList(request: GetBlockedListRequest): GetBlockedListResponse = error("not used in test")
    }

    /** Fails the SyncContacts RPC with a fixed exception. */
    private class ThrowingClient(
        private val error: Throwable,
    ) : ContactServiceClient {
        override suspend fun syncContacts(request: SyncContactsRequest): SyncContactsResponse = throw error

        override suspend fun getContacts(request: GetContactsRequest): GetContactsResponse = error("not used in test")

        override suspend fun blockContact(request: BlockContactRequest): BlockContactResponse = error("not used in test")

        override suspend fun unblockContact(request: UnblockContactRequest): UnblockContactResponse = error("not used in test")

        override suspend fun getBlockedList(request: GetBlockedListRequest): GetBlockedListResponse = error("not used in test")
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
