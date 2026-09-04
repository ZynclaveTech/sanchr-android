package com.sanchr.core.crypto.profile

import com.sanchr.core.crypto.profile.ProfileCrypto.ProfileField
import com.sanchr.proto.settings.ProfileResponse
import com.sanchr.proto.settings.SettingsServiceClient
import com.sanchr.proto.settings.UpdateProfileRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads the user's profile as ciphertext under their Profile Key, the way
 * iOS `ProfileUseCases.UpdateProfile` does.
 *
 * Only four fields go on the wire: the plaintext avatar CDN URL (the image
 * bytes are not considered sensitive), the three encrypted fields, and the
 * key version. Plaintext display name and status text are deliberately
 * **not** sent — sending them alongside the ciphertext would make the
 * encryption decorative — so the server's plaintext column stays at the
 * placeholder it seeded at registration.
 *
 * Empty semantics follow iOS and the server's COALESCE: an empty display
 * name is still encrypted (a 28-byte blob); an empty bio or avatar URL is
 * sent as a zero-length blob, which the server reads as "leave unchanged",
 * not "clear".
 */
@Singleton
class EncryptedProfileUpdater
    @Inject
    constructor(
        private val settingsClient: SettingsServiceClient,
        private val profileKeyStore: ProfileKeyStore,
    ) {
        suspend fun update(
            displayName: String,
            bio: String,
            avatarUrl: String,
        ): ProfileResponse {
            val key = profileKeyStore.ownProfileKey()
            return settingsClient.updateProfile(
                UpdateProfileRequest(
                    displayName = "",
                    bio = "",
                    avatarUrl = avatarUrl,
                    encryptedDisplayName = ProfileCrypto.encryptField(displayName, key, ProfileField.DISPLAY_NAME),
                    encryptedBio = if (bio.isEmpty()) ByteArray(0) else ProfileCrypto.encryptField(bio, key, ProfileField.BIO),
                    encryptedAvatarUrl =
                        if (avatarUrl.isEmpty()) ByteArray(0) else ProfileCrypto.encryptField(avatarUrl, key, ProfileField.AVATAR_URL),
                    profileKeyVersion = ProfileCrypto.version(key),
                ),
            )
        }
    }
