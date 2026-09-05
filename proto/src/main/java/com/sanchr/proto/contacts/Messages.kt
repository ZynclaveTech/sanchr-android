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

/**
 * A registered user resolved from the address-book intersection.
 *
 * [displayName] is the server's *plaintext* column, which for accounts that
 * have adopted the Profile Key is the placeholder the server seeded at
 * registration, never the real name — do not display it. The real name is
 * [encryptedDisplayName], decryptable only with the peer's Profile Key.
 * [phoneHash] is not on the wire and is always empty.
 */
@Serializable
data class MatchedContact(
    @SerialName("phone_hash") val phoneHash: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("phone_number") val phoneNumber: String = "",
    @SerialName("status_text") val statusText: String = "",
    @SerialName("encrypted_display_name") val encryptedDisplayName: ByteArray = ByteArray(0),
    @SerialName("encrypted_bio") val encryptedBio: ByteArray = ByteArray(0),
    @SerialName("encrypted_avatar_url") val encryptedAvatarUrl: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MatchedContact) return false
        return phoneHash == other.phoneHash &&
            userId == other.userId &&
            displayName == other.displayName &&
            avatarUrl == other.avatarUrl &&
            phoneNumber == other.phoneNumber &&
            statusText == other.statusText &&
            encryptedDisplayName.contentEquals(other.encryptedDisplayName) &&
            encryptedBio.contentEquals(other.encryptedBio) &&
            encryptedAvatarUrl.contentEquals(other.encryptedAvatarUrl)
    }

    override fun hashCode(): Int {
        var result = phoneHash.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + avatarUrl.hashCode()
        result = 31 * result + phoneNumber.hashCode()
        result = 31 * result + statusText.hashCode()
        result = 31 * result + encryptedDisplayName.contentHashCode()
        result = 31 * result + encryptedBio.contentHashCode()
        result = 31 * result + encryptedAvatarUrl.contentHashCode()
        return result
    }
}

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
    @SerialName("status_text") val statusText: String = "",
    @SerialName("encrypted_display_name") val encryptedDisplayName: ByteArray = ByteArray(0),
    @SerialName("encrypted_bio") val encryptedBio: ByteArray = ByteArray(0),
    @SerialName("encrypted_avatar_url") val encryptedAvatarUrl: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Contact) return false
        return userId == other.userId &&
            phoneNumber == other.phoneNumber &&
            displayName == other.displayName &&
            avatarUrl == other.avatarUrl &&
            bio == other.bio &&
            isOnline == other.isOnline &&
            lastSeen == other.lastSeen &&
            isBlocked == other.isBlocked &&
            statusText == other.statusText &&
            encryptedDisplayName.contentEquals(other.encryptedDisplayName) &&
            encryptedBio.contentEquals(other.encryptedBio) &&
            encryptedAvatarUrl.contentEquals(other.encryptedAvatarUrl)
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + phoneNumber.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + avatarUrl.hashCode()
        result = 31 * result + bio.hashCode()
        result = 31 * result + isOnline.hashCode()
        result = 31 * result + lastSeen.hashCode()
        result = 31 * result + isBlocked.hashCode()
        result = 31 * result + statusText.hashCode()
        result = 31 * result + encryptedDisplayName.contentHashCode()
        result = 31 * result + encryptedBio.contentHashCode()
        result = 31 * result + encryptedAvatarUrl.contentHashCode()
        return result
    }
}

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
