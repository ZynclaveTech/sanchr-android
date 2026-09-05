package com.sanchr.app.data

import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.dao.ContactProfileDao
import com.sanchr.core.database.entity.ContactEntity
import com.sanchr.core.database.entity.ContactProfileEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.domain.contacts.DiscoveryRepository
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
import io.mockk.every
import io.mockk.mockk
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
                repo(client, NoOpContactDao())

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
            // and only after discovery confirmed it, with the normalised number
            assertEquals(listOf("+15550001234"), discovery.queried.single())
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
                repo(client, NoOpContactDao())

            val user = repo.lookupByPhone("+15550001234")
            assertNotNull(user)
            requireNotNull(user)
            assertEquals("user-42", user.id)
            // The server's display_name is plaintext it should not have and is
            // never shown (ContactDisplayName); a lookup by number shows the
            // number until the peer's Profile Key arrives with their first
            // message.
            assertEquals("+15550001234", user.displayName)
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
                repo(client, NoOpContactDao())

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
                repo(client, NoOpContactDao())

            assertNull(repo.lookupByPhone("+15550009999"))
        }

    @Test
    fun `lookupByPhone propagates RPC failure to caller`() =
        runTest {
            val boom = RuntimeException("network down")
            val repo =
                repo(ThrowingClient(boom), NoOpContactDao())

            try {
                repo.lookupByPhone("+15550001234")
                fail("expected RuntimeException to bubble out of lookupByPhone")
            } catch (e: RuntimeException) {
                assertSame(boom, e)
            }
        }

    @Test
    fun `blocking is written locally even when the server call fails`() =
        runTest {
            val dao = RecordingContactDao()
            val repo = repo(BlockRejectingClient(), dao)

            repo.setBlocked("peer-uuid", blocked = true)

            // The local row is what ReceiveMessageUseCase reads to drop a
            // blocked sender, and for sealed 1:1 traffic the server cannot
            // enforce the block at all — so a failed sync must not undo or
            // prevent the only enforcement that works.
            assertEquals(listOf("peer-uuid" to true), dao.blockWrites)
        }

    @Test
    fun `a failed block sync does not surface as an error to the caller`() =
        runTest {
            val repo = repo(BlockRejectingClient(), RecordingContactDao())

            // Does not throw.
            repo.setBlocked("peer-uuid", blocked = true)
            repo.setBlocked("peer-uuid", blocked = false)
        }

    @Test
    fun `unblocking is written locally too`() =
        runTest {
            val dao = RecordingContactDao()
            val repo = repo(BlockRejectingClient(), dao)

            repo.setBlocked("peer-uuid", blocked = false)

            assertEquals(listOf("peer-uuid" to false), dao.blockWrites)
        }

    // ── Construction ─────────────────────────────────────────────────────

    /** Discovery that reports every queried number as registered, unless [registered] is given. */
    private val discovery = FakeDiscovery()

    private fun repo(
        client: ContactServiceClient,
        dao: ContactDao,
        discovery: DiscoveryRepository = this.discovery,
        own: String? = "+15550000000",
        profiles: ContactProfileDao = NoOpContactProfileDao(),
    ) = ContactRepositoryImpl(
        contactClient = client,
        contactDao = dao,
        contactProfileDao = profiles,
        discoveryRepository = discovery,
        sessionManager = mockk<SessionManager> { every { getStoredPhoneE164() } returns own },
    )

    // ── Discovery-first sync ─────────────────────────────────────────────

    @Test
    fun `SyncContacts only ever receives hashes of numbers discovery confirmed`() =
        runTest {
            // The property the feature exists for: the address book has three
            // numbers, discovery says one is registered, and the server is
            // asked to resolve exactly that one.
            val client = RecordingSyncContactsClient(response = SyncContactsResponse())
            val discovery = FakeDiscovery(registered = setOf("+15550001234"))

            repo(client, NoOpContactDao(), discovery)
                .discoverAndSyncContacts(listOf("(555) 000-1234", "(555) 000-5678", "+44 20 7123 4567"))

            assertEquals(
                "every normalised candidate is blinded and queried",
                setOf("+15550001234", "+15550005678", "+442071234567"),
                discovery.queried.single().toSet(),
            )
            val expectedHash = sha256Hex("+15550001234")
            assertEquals(listOf(expectedHash), client.capturedRequests.single().phoneHashes)
        }

    @Test
    fun `discovery failure propagates - the caller cannot fall back to bulk upload`() =
        runTest {
            val client = RecordingSyncContactsClient(response = SyncContactsResponse())
            val discovery =
                object : DiscoveryRepository {
                    override suspend fun discoverRegistered(phoneNumbersE164: List<String>): List<String> =
                        throw IllegalStateException("UNAVAILABLE")
                }

            try {
                repo(client, NoOpContactDao(), discovery).discoverAndSyncContacts(listOf("(555) 000-1234"))
                fail("expected the discovery failure to propagate")
            } catch (e: IllegalStateException) {
                assertEquals("UNAVAILABLE", e.message)
            }
            assertTrue("SyncContacts must not be called after a discovery failure", client.capturedRequests.isEmpty())
        }

    @Test
    fun `no registered matches means no resolution call and a count of zero`() =
        runTest {
            val client = RecordingSyncContactsClient(response = SyncContactsResponse())
            val discovery = FakeDiscovery(registered = emptySet())

            val count = repo(client, NoOpContactDao(), discovery).discoverAndSyncContacts(listOf("(555) 000-1234"))

            assertEquals(0, count)
            assertTrue(client.capturedRequests.isEmpty())
        }

    @Test
    fun `matches are stored as registered contacts and counted`() =
        runTest {
            val client =
                RecordingSyncContactsClient(
                    response =
                        SyncContactsResponse(
                            matchedContacts =
                                listOf(
                                    MatchedContact(userId = "u-1", displayName = "Ada", phoneNumber = "+15550001234"),
                                ),
                        ),
                )
            val dao = InsertRecordingContactDao()

            val count = repo(client, dao, FakeDiscovery(setOf("+15550001234"))).discoverAndSyncContacts(listOf("555-000-1234"))

            assertEquals(1, count)
            assertEquals(listOf("u-1"), dao.inserted.map { it.userId })
            assertTrue(dao.inserted.single().isRegistered)
        }

    @Test
    fun `lookupByPhone returns null without a resolution call when discovery finds nothing`() =
        runTest {
            val client = RecordingSyncContactsClient(response = SyncContactsResponse())

            val user = repo(client, NoOpContactDao(), FakeDiscovery(registered = emptySet())).lookupByPhone("+15550009999")

            assertNull(user)
            assertTrue("an unregistered number must never be hashed to the server", client.capturedRequests.isEmpty())
        }

    @Test
    fun `background syncContacts re-resolves only already-known registered numbers`() =
        runTest {
            val client = RecordingSyncContactsClient(response = SyncContactsResponse())
            val dao =
                object : NoOpContactDao() {
                    override suspend fun getAllContacts(): List<ContactEntity> =
                        listOf(
                            ContactEntity(
                                id = "u-1",
                                userId = "u-1",
                                phoneNumber = "+15550001234",
                                displayName = "Ada",
                                isRegistered = true,
                            ),
                            ContactEntity(id = "local", phoneNumber = "+15550007777", displayName = "Not on Sanchr", isRegistered = false),
                        )
                }

            repo(client, dao).syncContacts()

            assertEquals(listOf(sha256Hex("+15550001234")), client.capturedRequests.single().phoneHashes)
            assertTrue("background refresh must not run discovery", discovery.queried.isEmpty())
        }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    // ── Test doubles ──────────────────────────────────────────────────────

    /** Records what was queried; reports [registered] (or everything, when null) as registered. */
    private class FakeDiscovery(
        private val registered: Set<String>? = null,
    ) : DiscoveryRepository {
        val queried = mutableListOf<List<String>>()

        override suspend fun discoverRegistered(phoneNumbersE164: List<String>): List<String> {
            queried += phoneNumbersE164
            return registered?.let { r -> phoneNumbersE164.filter { it in r } } ?: phoneNumbersE164
        }
    }

    private class InsertRecordingContactDao : NoOpContactDao() {
        val inserted = mutableListOf<ContactEntity>()

        override suspend fun insertContacts(contacts: List<ContactEntity>) {
            inserted += contacts
        }
    }

    /** Records every local block-state write. */
    private class RecordingContactDao : NoOpContactDao() {
        val blockWrites = mutableListOf<Pair<String, Boolean>>()

        override suspend fun setBlocked(
            contactId: String,
            isBlocked: Boolean,
        ) {
            blockWrites += contactId to isBlocked
        }
    }

    /** Stands in for the unwired ContactService: both block RPCs fail. */
    private class BlockRejectingClient : ContactServiceClient {
        override suspend fun syncContacts(request: SyncContactsRequest): SyncContactsResponse = error("not used in test")

        override suspend fun getContacts(request: GetContactsRequest): GetContactsResponse = error("not used in test")

        override suspend fun blockContact(request: BlockContactRequest): BlockContactResponse =
            throw NotImplementedError("ContactService not wired yet")

        override suspend fun unblockContact(request: UnblockContactRequest): UnblockContactResponse =
            throw NotImplementedError("ContactService not wired yet")

        override suspend fun getBlockedList(request: GetBlockedListRequest): GetBlockedListResponse = error("not used in test")
    }

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
    private class FixedContactProfileDao(
        private val profiles: List<ContactProfileEntity>,
    ) : NoOpContactProfileDao() {
        override fun observeAll(): Flow<List<ContactProfileEntity>> = flowOf(profiles)
    }

    private open class NoOpContactProfileDao : ContactProfileDao {
        override suspend fun getByUserId(userId: String): ContactProfileEntity? = null

        override fun observeAll(): Flow<List<ContactProfileEntity>> = flowOf(emptyList())

        override suspend fun allUserIds(): List<String> = emptyList()

        override suspend fun upsert(profile: ContactProfileEntity) = Unit

        override suspend fun delete(userId: String) = Unit

        override suspend fun deleteAll() = Unit
    }

    private class FixedContactDao(
        private val contacts: List<ContactEntity>,
    ) : NoOpContactDao() {
        override fun observeContacts(): Flow<List<ContactEntity>> = flowOf(contacts)
    }

    private open class NoOpContactDao : ContactDao {
        override suspend fun isBlocked(contactId: String): Boolean? = null

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

    // ── Display-name precedence ─────────────────────────────────────────

    @Test
    fun `a contact's name is the address-book name, then the number, then the tilde profile name`() =
        runTest {
            val contacts =
                listOf(
                    ContactEntity(id = "u1", userId = "u1", phoneNumber = "+15550001", displayName = "Mum"),
                    ContactEntity(id = "u2", userId = "u2", phoneNumber = "+15550002", displayName = ""),
                    ContactEntity(id = "u3", userId = "u3", phoneNumber = "", displayName = ""),
                )
            val profiles =
                listOf(
                    ContactProfileEntity(userId = "u1", displayName = "Alice", updatedAt = 1L),
                    ContactProfileEntity(userId = "u2", displayName = "Bob", updatedAt = 1L),
                    ContactProfileEntity(userId = "u3", displayName = "Carol", bio = "hey", updatedAt = 1L),
                )

            val users =
                repo(
                    client = RecordingSyncContactsClient(SyncContactsResponse()),
                    dao = FixedContactDao(contacts),
                    profiles = FixedContactProfileDao(profiles),
                ).observeAllContacts().first()

            assertEquals(listOf("Mum", "+15550002", "~Carol"), users.map { it.displayName })
            assertEquals("hey", users[2].bio)
        }

    @Test
    fun `the server's placeholder name is never shown, even with no profile yet`() =
        runTest {
            val contacts = listOf(ContactEntity(id = "u1", userId = "u1", phoneNumber = "+15550001", displayName = "Sanchr User"))

            val users =
                repo(
                    client = RecordingSyncContactsClient(SyncContactsResponse()),
                    dao = FixedContactDao(contacts),
                ).observeAllContacts().first()

            assertEquals("+15550001", users.single().displayName)
        }
}
