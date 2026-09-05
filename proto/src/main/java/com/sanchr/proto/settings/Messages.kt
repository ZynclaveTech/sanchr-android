package com.sanchr.proto.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetSettingsRequest(
    @SerialName("user_id") val userId: String = "",
)

@Serializable
data class UserSettings(
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    val bio: String = "",
    val email: String = "",
    @SerialName("phone_number") val phoneNumber: String = "",
    @SerialName("notification_enabled") val notificationEnabled: Boolean = true,
    @SerialName("notification_sound") val notificationSound: String = "default",
    @SerialName("notification_vibrate") val notificationVibrate: Boolean = true,
    @SerialName("show_previews") val showPreviews: Boolean = true,
    @SerialName("read_receipts_enabled") val readReceiptsEnabled: Boolean = true,
    @SerialName("typing_indicators_enabled") val typingIndicatorsEnabled: Boolean = true,
    @SerialName("online_status_visible") val onlineStatusVisible: Boolean = true,
    // settings.proto declares `string profile_photo_visibility = 4`, and iOS
    // sends "everyone" / "contacts" / "nobody". This was a Boolean under the
    // name `profile_photo_visible`: wrong type and wrong field, so it could
    // never have round-tripped once settings sync is wired up.
    @SerialName("profile_photo_visibility") val profilePhotoVisibility: String = "everyone",
    @SerialName("about_visible") val aboutVisible: Boolean = true,
    @SerialName("sanchr_mode_enabled") val sanchrModeEnabled: Boolean = false,
    @SerialName("auto_download_media") val autoDownloadMedia: Boolean = true,
    @SerialName("media_quality") val mediaQuality: String = "standard",
    val theme: String = "system",
    val language: String = "en",
    @SerialName("font_size") val fontSize: String = "medium",
    /** Server-owned: set through `SetRegistrationLock`, never through `UpdateSettings`. */
    @SerialName("registration_lock_enabled") val registrationLockEnabled: Boolean = false,
)

@Serializable
data class UpdateSettingsRequest(
    val settings: UserSettings? = null,
    @SerialName("update_mask") val updateMask: List<String> = emptyList(),
)

/**
 * Profile update. The wire (`settings.proto`) carries plaintext `display_name`
 * / `status_text` only transitionally; iOS never sends them, and Android's
 * Profile Key path sends the `encrypted*` fields instead. Empty
 * [encryptedBio] / [encryptedAvatarUrl] mean "leave unchanged" — the server
 * COALESCEs — not "clear"; [encryptedDisplayName] is always ciphertext, even
 * for an empty name.
 */
@Serializable
data class UpdateProfileRequest(
    @SerialName("display_name") val displayName: String = "",
    /** Maps to the wire's `status_text`. */
    val bio: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("encrypted_display_name") val encryptedDisplayName: ByteArray = ByteArray(0),
    @SerialName("encrypted_bio") val encryptedBio: ByteArray = ByteArray(0),
    @SerialName("encrypted_avatar_url") val encryptedAvatarUrl: ByteArray = ByteArray(0),
    /** `SHA-256(profileKey)[0..16]`; echoed by the server so a peer can detect stale ciphertext. */
    @SerialName("profile_key_version") val profileKeyVersion: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UpdateProfileRequest) return false
        return displayName == other.displayName &&
            bio == other.bio &&
            avatarUrl == other.avatarUrl &&
            encryptedDisplayName.contentEquals(other.encryptedDisplayName) &&
            encryptedBio.contentEquals(other.encryptedBio) &&
            encryptedAvatarUrl.contentEquals(other.encryptedAvatarUrl) &&
            profileKeyVersion.contentEquals(other.profileKeyVersion)
    }

    override fun hashCode(): Int {
        var result = displayName.hashCode()
        result = 31 * result + bio.hashCode()
        result = 31 * result + avatarUrl.hashCode()
        result = 31 * result + encryptedDisplayName.contentHashCode()
        result = 31 * result + encryptedBio.contentHashCode()
        result = 31 * result + encryptedAvatarUrl.contentHashCode()
        result = 31 * result + profileKeyVersion.contentHashCode()
        return result
    }
}

@Serializable
data class ProfileResponse(
    /** Not on the wire: a completed RPC is a success. Kept for existing callers. */
    val success: Boolean = false,
    val id: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("status_text") val statusText: String = "",
    @SerialName("encrypted_display_name") val encryptedDisplayName: ByteArray = ByteArray(0),
    @SerialName("encrypted_bio") val encryptedBio: ByteArray = ByteArray(0),
    @SerialName("encrypted_avatar_url") val encryptedAvatarUrl: ByteArray = ByteArray(0),
    @SerialName("profile_key_version") val profileKeyVersion: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProfileResponse) return false
        return success == other.success &&
            id == other.id &&
            displayName == other.displayName &&
            avatarUrl == other.avatarUrl &&
            statusText == other.statusText &&
            encryptedDisplayName.contentEquals(other.encryptedDisplayName) &&
            encryptedBio.contentEquals(other.encryptedBio) &&
            encryptedAvatarUrl.contentEquals(other.encryptedAvatarUrl) &&
            profileKeyVersion.contentEquals(other.profileKeyVersion)
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + avatarUrl.hashCode()
        result = 31 * result + statusText.hashCode()
        result = 31 * result + encryptedDisplayName.contentHashCode()
        result = 31 * result + encryptedBio.contentHashCode()
        result = 31 * result + encryptedAvatarUrl.contentHashCode()
        result = 31 * result + profileKeyVersion.contentHashCode()
        return result
    }
}

/**
 * Another user's profile as the server holds it: ciphertext under *their*
 * Profile Key, readable only by peers that key has been sent to. [avatarUrl]
 * is a plaintext CDN URL, transitional; the server blanks both avatar fields
 * when the owner's photo-visibility setting excludes the caller.
 */
class UserProfile(
    val userId: String = "",
    val encryptedDisplayName: ByteArray = ByteArray(0),
    val encryptedBio: ByteArray = ByteArray(0),
    val encryptedAvatarUrl: ByteArray = ByteArray(0),
    val avatarUrl: String = "",
    val profileKeyVersion: ByteArray = ByteArray(0),
)

class GetUserProfilesRequest(
    val userIds: List<String>,
)

class GetUserProfilesResponse(
    val profiles: List<UserProfile>,
)

class SetRegistrationLockRequest(
    val enabled: Boolean,
    val pin: String = "",
    val currentPin: String = "",
)

class SetRegistrationLockResponse(
    val success: Boolean,
)

@Serializable
data class ToggleSanchrModeRequest(
    val enabled: Boolean = false,
)

@Serializable
data class GetStorageUsageRequest(
    @SerialName("user_id") val userId: String = "",
)

/**
 * The wire breaks usage down by media type (`photos/videos/documents/voice/
 * other`) plus a total and a quota. [mediaBytes] is derived from those for
 * the current UI; [messageBytes], [cacheBytes] and [vaultBytes] have no wire
 * source and stay zero — the Storage screen's categories predate the proto.
 */
@Serializable
data class StorageUsageResponse(
    @SerialName("total_bytes") val totalBytes: Long = 0L,
    @SerialName("media_bytes") val mediaBytes: Long = 0L,
    @SerialName("message_bytes") val messageBytes: Long = 0L,
    @SerialName("cache_bytes") val cacheBytes: Long = 0L,
    @SerialName("vault_bytes") val vaultBytes: Long = 0L,
    @SerialName("photos_bytes") val photosBytes: Long = 0L,
    @SerialName("videos_bytes") val videosBytes: Long = 0L,
    @SerialName("documents_bytes") val documentsBytes: Long = 0L,
    @SerialName("voice_bytes") val voiceBytes: Long = 0L,
    @SerialName("other_bytes") val otherBytes: Long = 0L,
    @SerialName("limit_bytes") val limitBytes: Long = 0L,
)
