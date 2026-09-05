package com.sanchr.domain.messaging

import android.util.Log
import com.sanchr.core.crypto.profile.ProfileCrypto
import com.sanchr.core.crypto.profile.ProfileCryptoException
import com.sanchr.core.crypto.profile.ProfileKeyStore
import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.dao.ContactProfileDao
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.entity.ContactProfileEntity
import com.sanchr.core.model.ContactDisplayName
import com.sanchr.proto.settings.GetUserProfilesRequest
import com.sanchr.proto.settings.SettingsServiceClient
import com.sanchr.proto.settings.UserProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Turns a peer's Profile Key into something to show: fetches the ciphertext
 * the server holds for them, decrypts it under the key they sent us, and
 * persists the result as a [ContactProfileEntity] plus a refreshed title on
 * every DIRECT conversation with them.
 *
 * The key arrives on every sealed payload they send (`sender_profile_key`)
 * and on a `profile-key/v1` control; [ReceiveMessageUseCase] records it via
 * [recordProfileKey] and calls [refresh] when it is new. The server never
 * sees a name: what it stores is AES-GCM under per-field HKDF subkeys of a
 * key only the peer's correspondents hold (see `ProfileCrypto`).
 */
@Singleton
class ContactProfileResolver
    @Inject
    constructor(
        private val profileKeyStore: ProfileKeyStore,
        private val settingsClient: SettingsServiceClient,
        private val contactDao: ContactDao,
        private val contactProfileDao: ContactProfileDao,
        private val conversationDao: ConversationDao,
    ) {
        /** Records [key] as [userId]'s current Profile Key. True when it differs from what was held. */
        fun recordProfileKey(
            userId: String,
            key: ByteArray,
        ): Boolean = profileKeyStore.saveContactProfileKey(userId, key)

        /**
         * Fetches and decrypts [userId]'s profile under the key on file, then
         * persists it. A no-op without a key. Never throws except
         * [CancellationException]: this runs off the receive path and nothing
         * about a profile is worth failing a message for.
         */
        suspend fun refresh(userId: String) {
            val key = profileKeyStore.contactProfileKey(userId) ?: return
            val profile = fetch(userId) ?: return

            // The server echoes SHA-256(key)[0..16] with the ciphertext. A
            // mismatch means the ciphertext is under a key we do not hold
            // (they rotated and we have not yet received the new one, or the
            // upload under the key we hold has not landed). Decrypting would
            // only fail authentication; keep whatever we already show.
            val expectedVersion = ProfileCrypto.version(key)
            if (profile.profileKeyVersion.isNotEmpty() && !profile.profileKeyVersion.contentEquals(expectedVersion)) {
                Log.d(TAG, "profile for $userId is under a different key version; not applying")
                return
            }

            val displayName = decrypt(profile.encryptedDisplayName, key, ProfileCrypto.ProfileField.DISPLAY_NAME)
            val bio = decrypt(profile.encryptedBio, key, ProfileCrypto.ProfileField.BIO)
            val avatarUrl =
                decrypt(profile.encryptedAvatarUrl, key, ProfileCrypto.ProfileField.AVATAR_URL)
                    ?: profile.avatarUrl.ifEmpty { null }
            if (displayName == null && bio == null && avatarUrl == null) {
                // Nothing decrypted: either the profile is genuinely empty or
                // every blob failed authentication. Neither is a reason to
                // erase a name we already resolved.
                Log.d(TAG, "profile for $userId yielded no fields; leaving existing profile untouched")
                return
            }

            contactProfileDao.upsert(
                ContactProfileEntity(
                    userId = userId,
                    displayName = displayName,
                    bio = bio,
                    avatarUrl = avatarUrl,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            retitleDirectConversations(userId, displayName)
        }

        /**
         * Re-resolves every peer we hold a key for. Run from the periodic
         * sync (and so on every launch, via `syncNow`): a peer who renamed
         * themselves under the same key changes nothing on the wire, so this
         * is the only way that rename reaches us. One failure does not stop
         * the rest — [refresh] swallows its own.
         */
        suspend fun refreshAll() {
            val userIds = profileKeyStore.contactUserIds()
            userIds.forEach { refresh(it) }
            if (userIds.isNotEmpty()) Log.d(TAG, "re-resolved ${userIds.size} contact profile(s)")
        }

        /** What to call [userId] right now, by [ContactDisplayName] precedence. */
        suspend fun displayNameFor(userId: String): String {
            val contact = contactDao.getContactById(userId)
            val profile = contactProfileDao.getByUserId(userId)
            return ContactDisplayName.resolve(contact?.displayName, contact?.phoneNumber, profile?.displayName)
        }

        private suspend fun fetch(userId: String): UserProfile? =
            try {
                settingsClient
                    .getUserProfiles(GetUserProfilesRequest(listOf(userId)))
                    .profiles
                    .firstOrNull { it.userId == userId }
                    .also { if (it == null) Log.d(TAG, "server returned no profile for $userId") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "profile fetch failed for $userId", e)
                null
            }

        private fun decrypt(
            blob: ByteArray,
            key: ByteArray,
            field: ProfileCrypto.ProfileField,
        ): String? {
            if (blob.isEmpty()) return null
            return try {
                ProfileCrypto.decryptField(blob, key, field).ifEmpty { null }
            } catch (e: ProfileCryptoException) {
                Log.w(TAG, "could not decrypt ${field.name} for a contact: ${e.message}")
                null
            }
        }

        private suspend fun retitleDirectConversations(
            userId: String,
            profileName: String?,
        ) {
            val contact = contactDao.getContactById(userId)
            val title = ContactDisplayName.resolve(contact?.displayName, contact?.phoneNumber, profileName)
            conversationDao.directConversationIdsWith("\"$userId\"").forEach { conversationId ->
                conversationDao.updateTitle(conversationId, title)
            }
        }

        private companion object {
            const val TAG = "ContactProfileResolver"
        }
    }
