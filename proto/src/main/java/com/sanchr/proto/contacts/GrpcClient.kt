package com.sanchr.proto.contacts

import com.google.protobuf.ByteString
import io.grpc.CallOptions
import io.grpc.Channel
import sanchr.contacts.ContactServiceGrpcKt
import sanchr.contacts.Contacts

/**
 * gRPC client interface for the ContactService.
 * Adapter over the generated `sanchr.contacts.ContactServiceGrpcKt.ContactServiceCoroutineStub`.
 *
 * [syncContacts] resolves phone hashes to user records. It is the resolution
 * step *after* OPRF discovery, called with the hashes of the intersection
 * only — the server necessarily learns those (it has to, to return the
 * accounts), but that is the intersection, not the address book. It must not
 * be called with the whole address book; see `DiscoveryRepository`.
 */
interface ContactServiceClient {
    suspend fun syncContacts(request: SyncContactsRequest): SyncContactsResponse

    suspend fun getContacts(request: GetContactsRequest): GetContactsResponse

    suspend fun blockContact(request: BlockContactRequest): BlockContactResponse

    suspend fun unblockContact(request: UnblockContactRequest): UnblockContactResponse

    suspend fun getBlockedList(request: GetBlockedListRequest): GetBlockedListResponse
}

class ContactServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : ContactServiceClient {
    private val stub by lazy { ContactServiceGrpcKt.ContactServiceCoroutineStub(channel, callOptions) }

    override suspend fun syncContacts(request: SyncContactsRequest): SyncContactsResponse = stub.syncContacts(request.toProto()).toModel()

    override suspend fun getContacts(request: GetContactsRequest): GetContactsResponse =
        stub.getContacts(Contacts.GetContactsRequest.getDefaultInstance()).toModel()

    override suspend fun blockContact(request: BlockContactRequest): BlockContactResponse {
        stub.blockContact(request.toProto())
        return BlockContactResponse(success = true)
    }

    override suspend fun unblockContact(request: UnblockContactRequest): UnblockContactResponse {
        stub.unblockContact(request.toProto())
        return UnblockContactResponse(success = true)
    }

    override suspend fun getBlockedList(request: GetBlockedListRequest): GetBlockedListResponse =
        stub.getBlockedList(Contacts.GetBlockedListRequest.getDefaultInstance()).toModel()
}

// region ── Mappers ─────────────────────────────────────────────────────────
// Internal so the wire contract is unit-testable without a channel. Several
// of the hand-written models do not match the proto field-for-field
// (`matched_contacts` vs `matches`, `user_id` vs `contact_user_id`, paging
// fields the proto does not have, a `phone_hash` the server never sends);
// the mapping is reconciled here explicitly rather than by name.

/** The wire carries raw 32-byte SHA-256 digests; the model carries lowercase hex. */
internal fun SyncContactsRequest.toProto(): Contacts.SyncContactsRequest =
    Contacts.SyncContactsRequest
        .newBuilder()
        .addAllPhoneHashes(phoneHashes.map { ByteString.copyFrom(it.hexToBytes()) })
        .build()

internal fun Contacts.SyncContactsResponse.toModel(): SyncContactsResponse =
    SyncContactsResponse(
        matchedContacts =
            matchesList.map { m ->
                MatchedContact(
                    userId = m.userId,
                    displayName = m.displayName,
                    avatarUrl = m.avatarUrl,
                    phoneNumber = m.phoneNumber,
                )
            },
    )

internal fun Contacts.GetContactsResponse.toModel(): GetContactsResponse = GetContactsResponse(contacts = contactsList.map { it.toModel() })

internal fun Contacts.Contact.toModel(): Contact =
    Contact(
        userId = userId,
        phoneNumber = phoneNumber,
        displayName = displayName,
        avatarUrl = avatarUrl,
        isBlocked = isBlocked,
    )

internal fun BlockContactRequest.toProto(): Contacts.BlockContactRequest =
    Contacts.BlockContactRequest
        .newBuilder()
        .setContactUserId(userId)
        .build()

internal fun UnblockContactRequest.toProto(): Contacts.UnblockContactRequest =
    Contacts.UnblockContactRequest
        .newBuilder()
        .setContactUserId(userId)
        .build()

/** The wire returns ids only; callers resolve names from the local contact table. */
internal fun Contacts.GetBlockedListResponse.toModel(): GetBlockedListResponse =
    GetBlockedListResponse(
        blockedUsers = blockedUserIdsList.map { Contact(userId = it, isBlocked = true) },
    )

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex string must have even length, got $length" }
    return ByteArray(length / 2) { i -> substring(2 * i, 2 * i + 2).toInt(16).toByte() }
}

// endregion
