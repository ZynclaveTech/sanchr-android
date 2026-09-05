package com.sanchr.core.crypto.profile

import com.sanchr.core.crypto.profile.ProfileCrypto.ProfileField
import com.sanchr.proto.settings.GetSettingsRequest
import com.sanchr.proto.settings.GetStorageUsageRequest
import com.sanchr.proto.settings.GetUserProfilesRequest
import com.sanchr.proto.settings.GetUserProfilesResponse
import com.sanchr.proto.settings.ProfileResponse
import com.sanchr.proto.settings.SetRegistrationLockRequest
import com.sanchr.proto.settings.SetRegistrationLockResponse
import com.sanchr.proto.settings.SettingsServiceClient
import com.sanchr.proto.settings.StorageUsageResponse
import com.sanchr.proto.settings.ToggleSanchrModeRequest
import com.sanchr.proto.settings.UpdateProfileRequest
import com.sanchr.proto.settings.UpdateSettingsRequest
import com.sanchr.proto.settings.UserSettings
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest

class EncryptedProfileUpdaterTest {
    private val key = ByteArray(32) { 0x42 }
    private val store = mockk<ProfileKeyStore> { every { ownProfileKey() } returns key }

    private class RecordingSettingsClient : SettingsServiceClient {
        val requests = mutableListOf<UpdateProfileRequest>()

        override suspend fun updateProfile(request: UpdateProfileRequest): ProfileResponse {
            requests += request
            return ProfileResponse(success = true)
        }

        override suspend fun getSettings(request: GetSettingsRequest): UserSettings = error("unused")

        override suspend fun updateSettings(request: UpdateSettingsRequest): UserSettings = error("unused")

        override suspend fun toggleSanchrMode(request: ToggleSanchrModeRequest): UserSettings = error("unused")

        override suspend fun getStorageUsage(request: GetStorageUsageRequest): StorageUsageResponse = error("unused")

        override suspend fun getUserProfiles(request: GetUserProfilesRequest): GetUserProfilesResponse = error("unused")

        override suspend fun setRegistrationLock(request: SetRegistrationLockRequest): SetRegistrationLockResponse = error("unused")
    }

    @Test
    fun `never uploads plaintext name or bio, and the ciphertext opens with the key`() =
        runTest {
            val client = RecordingSettingsClient()

            EncryptedProfileUpdater(client, store).update(displayName = "Alice", bio = "hi there", avatarUrl = "https://cdn/a.png")

            val sent = client.requests.single()
            assertEquals("", sent.displayName, "plaintext display name must not be sent")
            assertEquals("", sent.bio, "plaintext status text must not be sent")
            assertEquals("https://cdn/a.png", sent.avatarUrl, "the CDN URL is sent in the clear by design")
            assertFalse(sent.encryptedDisplayName.toString(Charsets.ISO_8859_1).contains("Alice"))
            assertEquals("Alice", ProfileCrypto.decryptField(sent.encryptedDisplayName, key, ProfileField.DISPLAY_NAME))
            assertEquals("hi there", ProfileCrypto.decryptField(sent.encryptedBio, key, ProfileField.BIO))
            assertEquals("https://cdn/a.png", ProfileCrypto.decryptField(sent.encryptedAvatarUrl, key, ProfileField.AVATAR_URL))
            assertContentEquals(ProfileCrypto.version(key), sent.profileKeyVersion)
        }

    @Test
    fun `empty bio and avatar are sent as empty blobs, empty name is still encrypted`() =
        runTest {
            val client = RecordingSettingsClient()

            EncryptedProfileUpdater(client, store).update(displayName = "", bio = "", avatarUrl = "")

            val sent = client.requests.single()
            assertEquals(0, sent.encryptedBio.size, "empty bio means 'leave unchanged' on the server")
            assertEquals(0, sent.encryptedAvatarUrl.size)
            assertEquals(28, sent.encryptedDisplayName.size, "the display name is always ciphertext, as on iOS")
        }
}
