package com.sanchr.proto.contacts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncContactsRequest(
    @SerialName("phone_hashes") val phoneHashes: List<String> = emptyList(),
)

@Serializable
data class SyncContactsResponse(
    @SerialName("matched_contacts") val matchedContacts: List<MatchedContact> = emptyList(),
)

@Serializable
data class MatchedContact(
    @SerialName("phone_hash") val phoneHash: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("phone_number") val phoneNumber: String = "",
)

@Serializable
data class GetContactsRequest(
    @SerialName("page_token") val pageToken: String = "",
    @SerialName("page_size") val pageSize: Int = 50,
)

@Serializable
data class GetContactsResponse(
    val contacts: List<Contact> = emptyList(),
    @SerialName("next_page_token") val nextPageToken: String = "",
)

@Serializable
data class Contact(
    @SerialName("user_id") val userId: String = "",
    @SerialName("phone_number") val phoneNumber: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    val bio: String = "",
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("last_seen") val lastSeen: Long = 0L,
    @SerialName("is_blocked") val isBlocked: Boolean = false,
)

@Serializable
data class BlockContactRequest(
    @SerialName("user_id") val userId: String = "",
)

@Serializable
data class BlockContactResponse(
    val success: Boolean = false,
)

@Serializable
data class UnblockContactRequest(
    @SerialName("user_id") val userId: String = "",
)

@Serializable
data class UnblockContactResponse(
    val success: Boolean = false,
)

@Serializable
data class GetBlockedListRequest(
    @SerialName("page_token") val pageToken: String = "",
    @SerialName("page_size") val pageSize: Int = 50,
)

@Serializable
data class GetBlockedListResponse(
    @SerialName("blocked_users") val blockedUsers: List<Contact> = emptyList(),
    @SerialName("next_page_token") val nextPageToken: String = "",
)

@Serializable
data class LookupUserRequest(
    @SerialName("phone_number") val phoneNumber: String = "",
)

/**
 * Response for [ContactServiceClient.lookupUser]. Mirrors the shape of
 * `sanchr.auth.User` (the canonical User message). `user` is null when no
 * registered user matches the phone — the GrpcClient surfaces NOT_FOUND as a
 * null return, so this data class stays non-nullable for consumers that
 * already have a non-null result.
 */
@Serializable
data class LookupUserResponse(
    val user: LookedUpUser? = null,
)

/**
 * Flat projection of `sanchr.auth.User` used only by [LookupUserResponse].
 * Named distinctly from [Contact] so it does not conflict with the richer
 * contact-list shape, and to keep the mapping to `core.model.User` explicit.
 */
@Serializable
data class LookedUpUser(
    val id: String = "",
    @SerialName("phone_number") val phoneNumber: String = "",
    @SerialName("display_name") val displayName: String = "",
    val email: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("status_text") val statusText: String = "",
    @SerialName("created_at") val createdAt: String = "",
)
