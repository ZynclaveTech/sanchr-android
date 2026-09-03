package com.sanchr.proto

import com.google.protobuf.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sanchr.contacts.Contacts
import sanchr.messaging.Messaging
import sanchr.settings.Settings
import sanchr.settings.SettingsServiceGrpc

/**
 * Pins the parts of the 2026-08 backend proto surface that Phase 1 builds on.
 *
 * `:proto` generates protobuf-lite ([com.google.protobuf.GeneratedMessageLite]) message
 * classes, which — unlike full protobuf-java — expose no `getDescriptor()` / field
 * reflection. These tests pin the surface by building and round-tripping real instances
 * instead, and by reflecting over the generated class's methods to prove a removed field's
 * accessor is gone. Either fails the same way a descriptor check would if the generated
 * surface regresses.
 */
class ProtoSurfaceTest {
    @Test
    fun `settings exposes GetUserProfiles and profile key versions`() {
        val methodNames = SettingsServiceGrpc.getServiceDescriptor().methods.map { it.fullMethodName }
        assertTrue(methodNames.contains("sanchr.settings.SettingsService/GetUserProfiles"))

        val version = ByteString.copyFromUtf8("v1")
        val updateRequest =
            Settings.UpdateProfileRequest
                .newBuilder()
                .setProfileKeyVersion(version)
                .build()
        assertEquals(version, updateRequest.profileKeyVersion)

        val profileResponse =
            Settings.ProfileResponse
                .newBuilder()
                .setProfileKeyVersion(version)
                .build()
        assertEquals(version, profileResponse.profileKeyVersion)

        val name = ByteString.copyFromUtf8("encrypted-name")
        val userProfile =
            Settings.UserProfile
                .newBuilder()
                .setEncryptedDisplayName(name)
                .build()
        assertEquals(name, userProfile.encryptedDisplayName)

        val getUserProfilesRequest =
            Settings.GetUserProfilesRequest
                .newBuilder()
                .addUserIds("user-1")
                .build()
        assertEquals(listOf("user-1"), getUserProfilesRequest.userIdsList)

        val getUserProfilesResponse =
            Settings.GetUserProfilesResponse
                .newBuilder()
                .addProfiles(userProfile)
                .build()
        assertEquals(listOf(userProfile), getUserProfilesResponse.profilesList)

        val lockRequest =
            Settings.SetRegistrationLockRequest
                .newBuilder()
                .setCurrentPin("1234")
                .build()
        assertEquals("1234", lockRequest.currentPin)
    }

    @Test
    fun `profile keys no longer travel over the server`() {
        assertFalse(hasMethod(Settings.UpdateProfileRequest::class.java, "getProfileKey"))
        assertFalse(hasMethod(Settings.ProfileResponse::class.java, "getProfileKey"))
        assertFalse(hasMethod(Contacts.MatchedContact::class.java, "getProfileKey"))
        assertFalse(hasMethod(Contacts.Contact::class.java, "getProfileKey"))
    }

    @Test
    fun `sealed device messages can be marked silent`() {
        val silent =
            Messaging.SealedDeviceMessage
                .newBuilder()
                .setSilent(true)
                .build()
        assertTrue(silent.silent)
        // proto3 encodes field 5 (silent) as tag byte 0x28 = (5 << 3) | 0 (varint wire type),
        // followed by the value 0x01. A mismatch here means the field number drifted from 5 —
        // exactly the cross-platform corruption this test exists to catch.
        assertEquals(listOf(0x28, 0x01), silent.toByteArray().toList().map { it.toInt() })

        val notSilent =
            Messaging.SealedDeviceMessage
                .newBuilder()
                .setSilent(false)
                .build()
        assertFalse(notSilent.silent)
        // proto3 omits default-valued fields from the wire entirely.
        assertEquals(emptyList<Int>(), notSilent.toByteArray().toList().map { it.toInt() })
    }

    private fun hasMethod(
        clazz: Class<*>,
        name: String,
    ): Boolean = clazz.methods.any { it.name == name }
}
