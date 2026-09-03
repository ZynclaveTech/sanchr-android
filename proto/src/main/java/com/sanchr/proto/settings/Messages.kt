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
    @SerialName("profile_photo_visible") val profilePhotoVisible: Boolean = true,
    @SerialName("about_visible") val aboutVisible: Boolean = true,
    @SerialName("sanchr_mode_enabled") val sanchrModeEnabled: Boolean = false,
    @SerialName("auto_download_media") val autoDownloadMedia: Boolean = true,
    @SerialName("media_quality") val mediaQuality: String = "standard",
    val theme: String = "system",
    val language: String = "en",
    @SerialName("font_size") val fontSize: String = "medium",
)

@Serializable
data class UpdateSettingsRequest(
    val settings: UserSettings? = null,
    @SerialName("update_mask") val updateMask: List<String> = emptyList(),
)

@Serializable
data class UpdateProfileRequest(
    @SerialName("display_name") val displayName: String = "",
    val bio: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    /** Opaque short hash the server echoes so a peer can detect stale profile ciphertext. */
    @SerialName("profile_key_version") val profileKeyVersion: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UpdateProfileRequest) return false
        return displayName == other.displayName &&
            bio == other.bio &&
            avatarUrl == other.avatarUrl &&
            profileKeyVersion.contentEquals(other.profileKeyVersion)
    }

    override fun hashCode(): Int {
        var result = displayName.hashCode()
        result = 31 * result + bio.hashCode()
        result = 31 * result + avatarUrl.hashCode()
        result = 31 * result + profileKeyVersion.contentHashCode()
        return result
    }
}

@Serializable
data class ProfileResponse(
    val success: Boolean = false,
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("profile_key_version") val profileKeyVersion: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProfileResponse) return false
        return success == other.success &&
            avatarUrl == other.avatarUrl &&
            profileKeyVersion.contentEquals(other.profileKeyVersion)
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + avatarUrl.hashCode()
        result = 31 * result + profileKeyVersion.contentHashCode()
        return result
    }
}

@Serializable
data class ToggleSanchrModeRequest(
    val enabled: Boolean = false,
)

@Serializable
data class GetStorageUsageRequest(
    @SerialName("user_id") val userId: String = "",
)

@Serializable
data class StorageUsageResponse(
    @SerialName("total_bytes") val totalBytes: Long = 0L,
    @SerialName("media_bytes") val mediaBytes: Long = 0L,
    @SerialName("message_bytes") val messageBytes: Long = 0L,
    @SerialName("cache_bytes") val cacheBytes: Long = 0L,
    @SerialName("vault_bytes") val vaultBytes: Long = 0L,
)
