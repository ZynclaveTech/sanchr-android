package com.sanchr.proto.contacts

import io.grpc.CallOptions
import io.grpc.Channel

/**
 * gRPC client interface for the ContactService.
 * Generated stub equivalent for sanchr.contacts.ContactService.
 */
interface ContactServiceClient {

    suspend fun syncContacts(request: SyncContactsRequest): SyncContactsResponse

    suspend fun getContacts(request: GetContactsRequest): GetContactsResponse

    suspend fun blockContact(request: BlockContactRequest): BlockContactResponse

    suspend fun unblockContact(request: UnblockContactRequest): UnblockContactResponse

    suspend fun getBlockedList(request: GetBlockedListRequest): GetBlockedListResponse
}

/**
 * Implementation shell that will delegate to the actual gRPC-generated stubs
 * once protobuf-gradle-plugin codegen runs.
 */
class ContactServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : ContactServiceClient {

    override suspend fun syncContacts(request: SyncContactsRequest): SyncContactsResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun getContacts(request: GetContactsRequest): GetContactsResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun blockContact(request: BlockContactRequest): BlockContactResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun unblockContact(request: UnblockContactRequest): UnblockContactResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun getBlockedList(request: GetBlockedListRequest): GetBlockedListResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }
}
