package com.sanchr.proto.contacts

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.Status
import io.grpc.StatusException
import sanchr.auth.Auth
import sanchr.contacts.ContactServiceGrpcKt
import sanchr.contacts.Contacts

/**
 * gRPC client interface for the ContactService.
 *
 * Only [lookupUser] is wired in M5 — it's the sole RPC the chats UI needs
 * (phone-based user lookup for "Start chat"). Contact sync, block/unblock,
 * and the blocked list land in M6 along with the full address-book sync UX.
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
     * Returns the matched [LookedUpUser] when found, or `null` if the server
     * responded with `NOT_FOUND`. Any other error (network, INVALID_ARGUMENT,
     * etc.) propagates as an exception.
     */
    suspend fun lookupUser(phoneNumber: String): LookedUpUser?
}

class ContactServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : ContactServiceClient {
    private val stub by lazy { ContactServiceGrpcKt.ContactServiceCoroutineStub(channel, callOptions) }

    // TODO(M6): wire to ContactServiceGrpcKt.ContactServiceCoroutineStub when contact sync lands
    override suspend fun syncContacts(request: SyncContactsRequest): SyncContactsResponse =
        throw NotImplementedError("Awaiting contact sync feature in M6+")

    // TODO(M6): wire to ContactServiceGrpcKt.ContactServiceCoroutineStub when contact sync lands
    override suspend fun getContacts(request: GetContactsRequest): GetContactsResponse =
        throw NotImplementedError("Awaiting contact sync feature in M6+")

    // TODO(M6): wire to ContactServiceGrpcKt.ContactServiceCoroutineStub when contact sync lands
    override suspend fun blockContact(request: BlockContactRequest): BlockContactResponse =
        throw NotImplementedError("Awaiting contact sync feature in M6+")

    // TODO(M6): wire to ContactServiceGrpcKt.ContactServiceCoroutineStub when contact sync lands
    override suspend fun unblockContact(request: UnblockContactRequest): UnblockContactResponse =
        throw NotImplementedError("Awaiting contact sync feature in M6+")

    // TODO(M6): wire to ContactServiceGrpcKt.ContactServiceCoroutineStub when contact sync lands
    override suspend fun getBlockedList(request: GetBlockedListRequest): GetBlockedListResponse =
        throw NotImplementedError("Awaiting contact sync feature in M6+")

    override suspend fun lookupUser(phoneNumber: String): LookedUpUser? {
        val request =
            Contacts.LookupUserRequest
                .newBuilder()
                .setPhoneNumber(phoneNumber)
                .build()
        return try {
            val response = stub.lookupUser(request)
            if (response.hasUser()) response.user.toManual() else null
        } catch (e: StatusException) {
            if (e.status.code == Status.Code.NOT_FOUND) null else throw e
        }
    }
}

private fun Auth.User.toManual(): LookedUpUser =
    LookedUpUser(
        id = id,
        phoneNumber = phoneNumber,
        displayName = displayName,
        email = email,
        avatarUrl = avatarUrl,
        statusText = statusText,
        createdAt = createdAt,
    )
