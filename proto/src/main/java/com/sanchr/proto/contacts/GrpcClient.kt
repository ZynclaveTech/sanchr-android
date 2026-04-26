package com.sanchr.proto.contacts

import io.grpc.CallOptions
import io.grpc.Channel

/**
 * gRPC client interface for the ContactService.
 *
 * Mirrors backend/proto/contacts.proto exactly: SyncContacts, GetContacts,
 * Block / Unblock, GetBlockedList. Phone-based user discovery is performed
 * via [syncContacts] with a single-element `phoneHashes` list — see
 * `ContactRepositoryImpl.lookupByPhone` and iOS `ContactRepository.searchUser`.
 * There is no separate LookupUser RPC (the backend never exposed one; the
 * deprecated backend-oss/ fork did, but that surface area was retired in the
 * Phase 1 backend canonicalization).
 */
interface ContactServiceClient {
    suspend fun syncContacts(request: SyncContactsRequest): SyncContactsResponse

    suspend fun getContacts(request: GetContactsRequest): GetContactsResponse

    suspend fun blockContact(request: BlockContactRequest): BlockContactResponse

    suspend fun unblockContact(request: UnblockContactRequest): UnblockContactResponse

    suspend fun getBlockedList(request: GetBlockedListRequest): GetBlockedListResponse
}

/**
 * Production [ContactServiceClient] implementation.
 *
 * The [channel] and [callOptions] are retained for the M6 wiring of
 * [ContactServiceGrpcKt.ContactServiceCoroutineStub][sanchr.contacts.ContactServiceGrpcKt.ContactServiceCoroutineStub]
 * (sync/get/block/unblock/getBlocked); they are intentionally unused
 * today because every M6 endpoint throws.
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

    override suspend fun syncContacts(request: SyncContactsRequest): SyncContactsResponse = unimplementedM6()

    override suspend fun getContacts(request: GetContactsRequest): GetContactsResponse = unimplementedM6()

    override suspend fun blockContact(request: BlockContactRequest): BlockContactResponse = unimplementedM6()

    override suspend fun unblockContact(request: UnblockContactRequest): UnblockContactResponse = unimplementedM6()

    override suspend fun getBlockedList(request: GetBlockedListRequest): GetBlockedListResponse = unimplementedM6()
}
