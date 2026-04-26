package com.sanchr.proto.contacts

import io.grpc.CallOptions
import io.grpc.Channel

/**
 * gRPC client interface for the ContactService.
 *
 * [lookupUser] is currently stubbed: backend/ does not expose a LookupUser
 * RPC (it only ever existed in the deprecated backend-oss/ fork). Phase 2
 * will reimplement phone-based lookup via SyncContacts(phoneHashes =
 * [SHA-256(normalized phone)]) — matching iOS ContactRepository.searchUser
 * — once the SyncContacts wiring lands. Contact sync, block/unblock, and
 * the blocked list land in M6 along with the full address-book sync UX.
 */
interface ContactServiceClient {
    suspend fun syncContacts(request: SyncContactsRequest): SyncContactsResponse

    suspend fun getContacts(request: GetContactsRequest): GetContactsResponse

    suspend fun blockContact(request: BlockContactRequest): BlockContactResponse

    suspend fun unblockContact(request: UnblockContactRequest): UnblockContactResponse

    suspend fun getBlockedList(request: GetBlockedListRequest): GetBlockedListResponse

    /**
     * Looks up a single registered user by E.164 phone number.
     *
     * TODO(Phase-2): rewrite via SyncContacts(phoneHashes = [SHA-256(normalized phone)])
     * matching iOS ContactRepository.searchUser. backend/ does not expose
     * a LookupUser RPC, so this currently returns `null` unconditionally.
     */
    suspend fun lookupUser(phoneNumber: String): LookedUpUser?
}

/**
 * Production [ContactServiceClient] implementation.
 *
 * The [channel] and [callOptions] are retained for the M6 wiring of
 * [ContactServiceGrpcKt.ContactServiceCoroutineStub][sanchr.contacts.ContactServiceGrpcKt.ContactServiceCoroutineStub]
 * (sync/get/block/unblock/getBlocked); they are intentionally unused
 * today because every M6 endpoint throws and [lookupUser] is stubbed
 * pending the Phase-2 SyncContacts rewrite.
 */
class ContactServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : ContactServiceClient {
    private fun unimplementedM6(): Nothing =
        throw NotImplementedError(
            "ContactService not wired yet (channel=$channel, callOptions=$callOptions); " +
                "lands in M6 along with the address-book sync UX.",
        )

    override suspend fun syncContacts(request: SyncContactsRequest): SyncContactsResponse =
        unimplementedM6()

    override suspend fun getContacts(request: GetContactsRequest): GetContactsResponse =
        unimplementedM6()

    override suspend fun blockContact(request: BlockContactRequest): BlockContactResponse =
        unimplementedM6()

    override suspend fun unblockContact(request: UnblockContactRequest): UnblockContactResponse =
        unimplementedM6()

    override suspend fun getBlockedList(request: GetBlockedListRequest): GetBlockedListResponse =
        unimplementedM6()

    /**
     * TODO(Phase-2): rewrite via SyncContacts(phoneHashes = [SHA-256(normalized phone)])
     * matching iOS ContactRepository.searchUser. backend/ does not expose LookupUser.
     */
    override suspend fun lookupUser(phoneNumber: String): LookedUpUser? = null
}
