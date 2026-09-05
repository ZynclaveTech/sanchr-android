package com.sanchr.proto.settings

import com.google.protobuf.ByteString
import io.grpc.CallOptions
import io.grpc.Channel
import sanchr.settings.Settings
import sanchr.settings.SettingsServiceGrpcKt

/**
 * gRPC client interface for the SettingsService.
 * Adapter over the generated `sanchr.settings.SettingsServiceGrpcKt.SettingsServiceCoroutineStub`.
 */
interface SettingsServiceClient {
    suspend fun getSettings(request: GetSettingsRequest): UserSettings

    /**
     * The wire's `UpdateSettingsRequest` carries a whole `UserSettings` and
     * has **no update mask**, while [UserSettings] here models only a subset
     * of its fields. The implementation therefore reads the server's current
     * settings and overlays only the fields this model carries before
     * writing, so a save from Android cannot silently reset a setting that
     * was made from iOS. [UpdateSettingsRequest.updateMask] is ignored: there
     * is nothing on the wire for it to drive.
     */
    suspend fun updateSettings(request: UpdateSettingsRequest): UserSettings

    suspend fun updateProfile(request: UpdateProfileRequest): ProfileResponse

    suspend fun toggleSanchrMode(request: ToggleSanchrModeRequest): UserSettings

    suspend fun getStorageUsage(request: GetStorageUsageRequest): StorageUsageResponse

    /** Other users' encrypted profiles, decryptable only with their Profile Key. */
    suspend fun getUserProfiles(request: GetUserProfilesRequest): GetUserProfilesResponse

    suspend fun setRegistrationLock(request: SetRegistrationLockRequest): SetRegistrationLockResponse
}

class SettingsServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : SettingsServiceClient {
    private val stub by lazy { SettingsServiceGrpcKt.SettingsServiceCoroutineStub(channel, callOptions) }

    override suspend fun getSettings(request: GetSettingsRequest): UserSettings =
        stub.getSettings(Settings.GetSettingsRequest.getDefaultInstance()).toModel()

    override suspend fun updateSettings(request: UpdateSettingsRequest): UserSettings {
        val current = stub.getSettings(Settings.GetSettingsRequest.getDefaultInstance())
        val merged = request.settings?.overlayOnto(current) ?: current
        return stub
            .updateSettings(
                Settings.UpdateSettingsRequest
                    .newBuilder()
                    .setSettings(merged)
                    .build(),
            ).toModel()
    }

    override suspend fun updateProfile(request: UpdateProfileRequest): ProfileResponse = stub.updateProfile(request.toProto()).toModel()

    override suspend fun toggleSanchrMode(request: ToggleSanchrModeRequest): UserSettings =
        stub
            .toggleSanchrMode(
                Settings.ToggleSanchrModeRequest
                    .newBuilder()
                    .setEnabled(request.enabled)
                    .build(),
            ).toModel()

    override suspend fun getStorageUsage(request: GetStorageUsageRequest): StorageUsageResponse =
        stub.getStorageUsage(Settings.GetStorageUsageRequest.getDefaultInstance()).toModel()

    override suspend fun getUserProfiles(request: GetUserProfilesRequest): GetUserProfilesResponse =
        stub
            .getUserProfiles(
                Settings.GetUserProfilesRequest
                    .newBuilder()
                    .addAllUserIds(request.userIds)
                    .build(),
            ).toModel()

    override suspend fun setRegistrationLock(request: SetRegistrationLockRequest): SetRegistrationLockResponse =
        stub
            .setRegistrationLock(
                Settings.SetRegistrationLockRequest
                    .newBuilder()
                    .setEnabled(request.enabled)
                    .setPin(request.pin)
                    .setCurrentPin(request.currentPin)
                    .build(),
            ).let { SetRegistrationLockResponse(success = it.success) }
}

// region ── Mappers ─────────────────────────────────────────────────────────
// Internal so the wire contract is unit-testable without a channel. The
// hand-written UserSettings predates the proto and carries fields the wire
// does not (profile identity, email, language…), while the wire carries
// fields the model does not (screen lock, notification categories, wallpaper,
// per-network downloads…). Only the overlap is mapped, and writes go through
// overlayOnto() so the rest survives a round trip untouched.

internal fun Settings.UserSettings.toModel(): UserSettings =
    UserSettings(
        readReceiptsEnabled = readReceipts,
        onlineStatusVisible = onlineStatusVisible,
        typingIndicatorsEnabled = typingIndicator,
        profilePhotoVisibility = profilePhotoVisibility.ifEmpty { "everyone" },
        sanchrModeEnabled = sanchrModeEnabled,
        notificationEnabled = messageNotifications,
        notificationSound = notificationSound.ifEmpty { "default" },
        notificationVibrate = notificationVibrate,
        showPreviews = showPreview,
        theme = theme.ifEmpty { "system" },
        fontSize = fontSize.ifEmpty { "medium" },
        // The model's single boolean means "also over mobile data"; Wi-Fi is
        // always allowed. Read the mobile policy back accordingly.
        autoDownloadMedia = autoDownloadMobile == AUTO_DOWNLOAD_ALWAYS,
    )

/** Writes only the fields this model carries onto [current]; every other wire field is preserved. */
internal fun UserSettings.overlayOnto(current: Settings.UserSettings): Settings.UserSettings =
    current
        .toBuilder()
        .setReadReceipts(readReceiptsEnabled)
        .setOnlineStatusVisible(onlineStatusVisible)
        .setTypingIndicator(typingIndicatorsEnabled)
        .setProfilePhotoVisibility(profilePhotoVisibility)
        .setSanchrModeEnabled(sanchrModeEnabled)
        .setMessageNotifications(notificationEnabled)
        .setNotificationSound(notificationSound)
        .setNotificationVibrate(notificationVibrate)
        .setShowPreview(showPreviews)
        .setTheme(theme)
        .setFontSize(fontSize)
        .setAutoDownloadWifi(AUTO_DOWNLOAD_ALWAYS)
        .setAutoDownloadMobile(if (autoDownloadMedia) AUTO_DOWNLOAD_ALWAYS else AUTO_DOWNLOAD_NEVER)
        .build()

internal fun UpdateProfileRequest.toProto(): Settings.UpdateProfileRequest =
    Settings.UpdateProfileRequest
        .newBuilder()
        .setDisplayName(displayName)
        .setAvatarUrl(avatarUrl)
        .setStatusText(bio)
        .setEncryptedDisplayName(ByteString.copyFrom(encryptedDisplayName))
        .setEncryptedBio(ByteString.copyFrom(encryptedBio))
        .setEncryptedAvatarUrl(ByteString.copyFrom(encryptedAvatarUrl))
        .setProfileKeyVersion(ByteString.copyFrom(profileKeyVersion))
        .build()

internal fun Settings.ProfileResponse.toModel(): ProfileResponse =
    ProfileResponse(
        success = true,
        id = id,
        displayName = displayName,
        avatarUrl = avatarUrl,
        statusText = statusText,
        encryptedDisplayName = encryptedDisplayName.toByteArray(),
        encryptedBio = encryptedBio.toByteArray(),
        encryptedAvatarUrl = encryptedAvatarUrl.toByteArray(),
        profileKeyVersion = profileKeyVersion.toByteArray(),
    )

internal fun Settings.UserProfile.toModel(): UserProfile =
    UserProfile(
        userId = userId,
        encryptedDisplayName = encryptedDisplayName.toByteArray(),
        encryptedBio = encryptedBio.toByteArray(),
        encryptedAvatarUrl = encryptedAvatarUrl.toByteArray(),
        avatarUrl = avatarUrl,
        profileKeyVersion = profileKeyVersion.toByteArray(),
    )

internal fun Settings.GetUserProfilesResponse.toModel(): GetUserProfilesResponse =
    GetUserProfilesResponse(profiles = profilesList.map { it.toModel() })

internal fun Settings.StorageUsageResponse.toModel(): StorageUsageResponse =
    StorageUsageResponse(
        totalBytes = totalBytes,
        mediaBytes = photosBytes + videosBytes + voiceBytes,
        photosBytes = photosBytes,
        videosBytes = videosBytes,
        documentsBytes = documentsBytes,
        voiceBytes = voiceBytes,
        otherBytes = otherBytes,
        limitBytes = limitBytes,
    )

private const val AUTO_DOWNLOAD_ALWAYS = "always"
private const val AUTO_DOWNLOAD_NEVER = "never"

// endregion
