package com.sanchr.domain.messaging

import com.sanchr.core.crypto.profile.ProfileCrypto
import com.sanchr.core.crypto.profile.ProfileKeyStore
import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.dao.ContactProfileDao
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.entity.ContactEntity
import com.sanchr.core.database.entity.ContactProfileEntity
import com.sanchr.proto.settings.GetUserProfilesResponse
import com.sanchr.proto.settings.SettingsServiceClient
import com.sanchr.proto.settings.UserProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactProfileResolverTest {
    private val key = ByteArray(32) { 0x11 }
    private val otherKey = ByteArray(32) { 0x22 }

    private val profileKeyStore = mockk<ProfileKeyStore>()
    private val settingsClient = mockk<SettingsServiceClient>()
    private val contactDao = mockk<ContactDao>()
    private val contactProfileDao = mockk<ContactProfileDao>(relaxed = true)
    private val conversationDao = mockk<ConversationDao>(relaxed = true)

    private val resolver = ContactProfileResolver(profileKeyStore, settingsClient, contactDao, contactProfileDao, conversationDao)

    private fun serverProfile(
        under: ByteArray = key,
        name: String = "Alice",
        bio: String = "hi",
        avatar: String = "https://cdn/a.jpg",
        version: ByteArray = ProfileCrypto.version(under),
    ) = UserProfile(
        userId = "alice",
        encryptedDisplayName = ProfileCrypto.encryptField(name, under, ProfileCrypto.ProfileField.DISPLAY_NAME),
        encryptedBio = ProfileCrypto.encryptField(bio, under, ProfileCrypto.ProfileField.BIO),
        encryptedAvatarUrl = ProfileCrypto.encryptField(avatar, under, ProfileCrypto.ProfileField.AVATAR_URL),
        avatarUrl = avatar,
        profileKeyVersion = version,
    )

    private fun primeKey(k: ByteArray? = key) {
        every { profileKeyStore.contactProfileKey("alice") } returns k
    }

    @Test
    fun `without a key on file nothing is fetched`() =
        runTest {
            primeKey(null)

            resolver.refresh("alice")

            coVerify(exactly = 0) { settingsClient.getUserProfiles(any()) }
        }

    @Test
    fun `decrypts every field under the key and persists them`() =
        runTest {
            primeKey()
            coEvery { settingsClient.getUserProfiles(any()) } returns GetUserProfilesResponse(listOf(serverProfile()))
            coEvery { contactDao.getContactById("alice") } returns null
            coEvery { conversationDao.directConversationIdsWith(any()) } returns emptyList()
            val stored = slot<ContactProfileEntity>()

            resolver.refresh("alice")

            coVerify { contactProfileDao.upsert(capture(stored)) }
            assertEquals("alice", stored.captured.userId)
            assertEquals("Alice", stored.captured.displayName)
            assertEquals("hi", stored.captured.bio)
            assertEquals("https://cdn/a.jpg", stored.captured.avatarUrl)
        }

    @Test
    fun `an unknown sender's direct conversations are retitled with the tilde profile name`() =
        runTest {
            primeKey()
            coEvery { settingsClient.getUserProfiles(any()) } returns GetUserProfilesResponse(listOf(serverProfile()))
            coEvery { contactDao.getContactById("alice") } returns null
            coEvery { conversationDao.directConversationIdsWith("\"alice\"") } returns listOf("conv-1", "conv-2")

            resolver.refresh("alice")

            coVerify { conversationDao.updateTitle("conv-1", "~Alice") }
            coVerify { conversationDao.updateTitle("conv-2", "~Alice") }
        }

    @Test
    fun `a contact with a phone number keeps the number as the title`() =
        runTest {
            primeKey()
            coEvery { settingsClient.getUserProfiles(any()) } returns GetUserProfilesResponse(listOf(serverProfile()))
            coEvery { contactDao.getContactById("alice") } returns
                ContactEntity(id = "alice", userId = "alice", phoneNumber = "+15550001", displayName = "Sanchr User")
            coEvery { conversationDao.directConversationIdsWith("\"alice\"") } returns listOf("conv-1")

            resolver.refresh("alice")

            coVerify { conversationDao.updateTitle("conv-1", "+15550001") }
            coVerify { contactProfileDao.upsert(match { it.displayName == "Alice" }) }
        }

    @Test
    fun `ciphertext under a different key version is left alone`() =
        runTest {
            primeKey()
            coEvery { settingsClient.getUserProfiles(any()) } returns
                GetUserProfilesResponse(listOf(serverProfile(under = otherKey)))

            resolver.refresh("alice")

            coVerify(exactly = 0) { contactProfileDao.upsert(any()) }
            coVerify(exactly = 0) { conversationDao.updateTitle(any(), any()) }
        }

    @Test
    fun `a blob that fails authentication is dropped, not thrown, and the other fields still land`() =
        runTest {
            primeKey()
            val tampered =
                serverProfile().let {
                    UserProfile(
                        userId = it.userId,
                        encryptedDisplayName = it.encryptedDisplayName.also { b -> b[b.lastIndex] = (b[b.lastIndex] + 1).toByte() },
                        encryptedBio = it.encryptedBio,
                        encryptedAvatarUrl = it.encryptedAvatarUrl,
                        avatarUrl = it.avatarUrl,
                        profileKeyVersion = it.profileKeyVersion,
                    )
                }
            coEvery { settingsClient.getUserProfiles(any()) } returns GetUserProfilesResponse(listOf(tampered))
            coEvery { contactDao.getContactById("alice") } returns null
            coEvery { conversationDao.directConversationIdsWith(any()) } returns emptyList()
            val stored = slot<ContactProfileEntity>()

            resolver.refresh("alice")

            coVerify { contactProfileDao.upsert(capture(stored)) }
            assertNull(stored.captured.displayName)
            assertEquals("hi", stored.captured.bio)
        }

    @Test
    fun `an empty profile does not erase what was resolved before`() =
        runTest {
            primeKey()
            coEvery { settingsClient.getUserProfiles(any()) } returns
                GetUserProfilesResponse(listOf(UserProfile(userId = "alice", profileKeyVersion = ProfileCrypto.version(key))))

            resolver.refresh("alice")

            coVerify(exactly = 0) { contactProfileDao.upsert(any()) }
        }

    @Test
    fun `a failed fetch is swallowed`() =
        runTest {
            primeKey()
            coEvery { settingsClient.getUserProfiles(any()) } throws IllegalStateException("UNAVAILABLE")

            resolver.refresh("alice")

            coVerify(exactly = 0) { contactProfileDao.upsert(any()) }
        }

    @Test
    fun `displayNameFor follows the precedence`() =
        runTest {
            coEvery { contactDao.getContactById("alice") } returns null
            coEvery { contactProfileDao.getByUserId("alice") } returns
                ContactProfileEntity(userId = "alice", displayName = "Alice", updatedAt = 1L)
            assertEquals("~Alice", resolver.displayNameFor("alice"))

            coEvery { contactDao.getContactById("alice") } returns
                ContactEntity(id = "alice", userId = "alice", phoneNumber = "+15550001", displayName = "Mum")
            assertEquals("Mum", resolver.displayNameFor("alice"))
        }

    @Test
    fun `refreshAll walks every peer we hold a key for and survives one failing`() =
        runTest {
            every { profileKeyStore.contactUserIds() } returns listOf("alice", "bob")
            every { profileKeyStore.contactProfileKey("alice") } returns key
            every { profileKeyStore.contactProfileKey("bob") } returns key
            coEvery { settingsClient.getUserProfiles(match { it.userIds == listOf("alice") }) } throws IllegalStateException("UNAVAILABLE")
            coEvery { settingsClient.getUserProfiles(match { it.userIds == listOf("bob") }) } returns
                GetUserProfilesResponse(
                    listOf(
                        UserProfile(
                            userId = "bob",
                            encryptedDisplayName = ProfileCrypto.encryptField("Bob", key, ProfileCrypto.ProfileField.DISPLAY_NAME),
                            profileKeyVersion = ProfileCrypto.version(key),
                        ),
                    ),
                )
            coEvery { contactDao.getContactById(any()) } returns null
            coEvery { conversationDao.directConversationIdsWith(any()) } returns emptyList()

            resolver.refreshAll()

            coVerify { contactProfileDao.upsert(match { it.userId == "bob" && it.displayName == "Bob" }) }
        }
}
