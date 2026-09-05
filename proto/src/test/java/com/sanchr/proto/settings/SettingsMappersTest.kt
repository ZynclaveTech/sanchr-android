package com.sanchr.proto.settings

import com.google.protobuf.ByteString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import sanchr.settings.Settings

class SettingsMappersTest {
    private fun bytes(
        fill: Int,
        n: Int,
    ) = ByteArray(n) { fill.toByte() }

    // ── The clobber guard: the reason updateSettings reads before it writes ──

    @Test
    fun `overlay preserves every wire field the model does not carry`() {
        // Settings a user made from iOS that Android's model has no notion of.
        val current =
            Settings.UserSettings
                .newBuilder()
                .setScreenLockEnabled(true)
                .setScreenLockTimeout(300)
                .setScreenshotProtection(true)
                .setBiometricLock(true)
                .setGroupNotifications(false)
                .setCallNotifications(false)
                .setChatWallpaper("dunes")
                .setAutoDownloadRoaming("never")
                .setLowDataMode(true)
                .setRegistrationLockEnabled(true)
                .setReadReceipts(false)
                .build()

        val merged = UserSettings(readReceiptsEnabled = true).overlayOnto(current)

        // The one field the model changed:
        assertTrue(merged.readReceipts)
        // Everything else exactly as it was — a naive mapper would have reset all of these.
        assertTrue(merged.screenLockEnabled)
        assertEquals(300, merged.screenLockTimeout)
        assertTrue(merged.screenshotProtection)
        assertTrue(merged.biometricLock)
        assertFalse(merged.groupNotifications)
        assertFalse(merged.callNotifications)
        assertEquals("dunes", merged.chatWallpaper)
        assertEquals("never", merged.autoDownloadRoaming)
        assertTrue(merged.lowDataMode)
        assertTrue(merged.registrationLockEnabled)
    }

    @Test
    fun `overlapping settings round-trip through the wire`() {
        val model =
            UserSettings(
                readReceiptsEnabled = false,
                onlineStatusVisible = false,
                typingIndicatorsEnabled = false,
                profilePhotoVisibility = "contacts",
                sanchrModeEnabled = true,
                notificationEnabled = false,
                notificationSound = "chime",
                notificationVibrate = false,
                showPreviews = false,
                theme = "dark",
                fontSize = "large",
                autoDownloadMedia = false,
            )

        val back = model.overlayOnto(Settings.UserSettings.getDefaultInstance()).toModel()

        assertEquals(model.readReceiptsEnabled, back.readReceiptsEnabled)
        assertEquals(model.onlineStatusVisible, back.onlineStatusVisible)
        assertEquals(model.typingIndicatorsEnabled, back.typingIndicatorsEnabled)
        assertEquals("contacts", back.profilePhotoVisibility)
        assertEquals(model.sanchrModeEnabled, back.sanchrModeEnabled)
        assertEquals(model.notificationEnabled, back.notificationEnabled)
        assertEquals("chime", back.notificationSound)
        assertEquals(model.notificationVibrate, back.notificationVibrate)
        assertEquals(model.showPreviews, back.showPreviews)
        assertEquals("dark", back.theme)
        assertEquals("large", back.fontSize)
        assertEquals(false, back.autoDownloadMedia)
    }

    @Test
    fun `auto-download boolean means mobile data, wifi is always allowed`() {
        val on = UserSettings(autoDownloadMedia = true).overlayOnto(Settings.UserSettings.getDefaultInstance())
        assertEquals("always", on.autoDownloadWifi)
        assertEquals("always", on.autoDownloadMobile)
        val off = UserSettings(autoDownloadMedia = false).overlayOnto(Settings.UserSettings.getDefaultInstance())
        assertEquals("always", off.autoDownloadWifi)
        assertEquals("never", off.autoDownloadMobile)
    }

    @Test
    fun `an empty wire string reads back as the model default, not as empty`() {
        val m = Settings.UserSettings.getDefaultInstance().toModel()
        assertEquals("everyone", m.profilePhotoVisibility)
        assertEquals("system", m.theme)
        assertEquals("medium", m.fontSize)
        assertEquals("default", m.notificationSound)
    }

    // ── Profile ─────────────────────────────────────────────────────────

    @Test
    fun `profile update sends bio as status_text and the ciphertext fields as raw bytes`() {
        val req =
            UpdateProfileRequest(
                displayName = "",
                bio = "hello",
                avatarUrl = "https://cdn/x.png",
                encryptedDisplayName = bytes(1, 28),
                encryptedBio = ByteArray(0),
                encryptedAvatarUrl = bytes(3, 40),
                profileKeyVersion = bytes(9, 16),
            ).toProto()

        assertEquals("hello", req.statusText)
        assertEquals("https://cdn/x.png", req.avatarUrl)
        assertContentEquals(bytes(1, 28), req.encryptedDisplayName.toByteArray())
        assertEquals(0, req.encryptedBio.size(), "empty bio ciphertext must stay empty: it means 'leave unchanged'")
        assertContentEquals(bytes(3, 40), req.encryptedAvatarUrl.toByteArray())
        assertContentEquals(bytes(9, 16), req.profileKeyVersion.toByteArray())
    }

    @Test
    fun `profile response maps every wire field and synthesises success`() {
        val proto =
            Settings.ProfileResponse
                .newBuilder()
                .setId("u-1")
                .setDisplayName("Sanchr User")
                .setStatusText("s")
                .setAvatarUrl("a")
                .setEncryptedDisplayName(ByteString.copyFrom(bytes(1, 28)))
                .setProfileKeyVersion(ByteString.copyFrom(bytes(9, 16)))
                .build()

        val m = proto.toModel()

        assertTrue(m.success)
        assertEquals("u-1", m.id)
        assertEquals("Sanchr User", m.displayName)
        assertEquals("s", m.statusText)
        assertEquals("a", m.avatarUrl)
        assertContentEquals(bytes(1, 28), m.encryptedDisplayName)
        assertContentEquals(bytes(9, 16), m.profileKeyVersion)
    }

    @Test
    fun `user profiles map user ids, blobs, plaintext avatar and version`() {
        val proto =
            Settings.GetUserProfilesResponse
                .newBuilder()
                .addProfiles(
                    Settings.UserProfile
                        .newBuilder()
                        .setUserId("u-2")
                        .setEncryptedDisplayName(ByteString.copyFrom(bytes(2, 30)))
                        .setEncryptedBio(ByteString.copyFrom(bytes(4, 31)))
                        .setAvatarUrl("https://cdn/u2.png")
                        .setProfileKeyVersion(ByteString.copyFrom(bytes(8, 16))),
                ).build()

        val m = proto.toModel()

        assertEquals(1, m.profiles.size)
        with(m.profiles[0]) {
            assertEquals("u-2", userId)
            assertContentEquals(bytes(2, 30), encryptedDisplayName)
            assertContentEquals(bytes(4, 31), encryptedBio)
            assertEquals(0, encryptedAvatarUrl.size)
            assertEquals("https://cdn/u2.png", avatarUrl)
            assertContentEquals(bytes(8, 16), profileKeyVersion)
        }
    }

    @Test
    fun `storage usage carries the wire breakdown and derives the media roll-up`() {
        val m =
            Settings.StorageUsageResponse
                .newBuilder()
                .setPhotosBytes(10)
                .setVideosBytes(20)
                .setDocumentsBytes(5)
                .setVoiceBytes(3)
                .setOtherBytes(1)
                .setTotalBytes(39)
                .setLimitBytes(1_000)
                .build()
                .toModel()

        assertEquals(39, m.totalBytes)
        assertEquals(33, m.mediaBytes)
        assertEquals(5, m.documentsBytes)
        assertEquals(1_000, m.limitBytes)
    }
}
