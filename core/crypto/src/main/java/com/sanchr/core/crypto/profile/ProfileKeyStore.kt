package com.sanchr.core.crypto.profile

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed storage for Profile Keys: our own, and one per contact.
 *
 * Mirrors iOS `ProfileKeyStore` semantics: the own key is generated once and
 * never rotates on its own (a reinstall mints a fresh one, and it reaches
 * peers on the next envelope); a contact's key is last-writer-wins with no
 * version check, exactly as iOS does. Unlike iOS there is no cloud-synced
 * copy of the own key, so a reinstall changes it — by design that is
 * self-healing rather than a failure.
 */
@Singleton
class ProfileKeyStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs by lazy {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        /** Our key, generated on first use. Never leaves the device except inside sealed envelopes. */
        @Synchronized
        fun ownProfileKey(): ByteArray {
            prefs.getString(KEY_OWN, null)?.let { return Base64.getDecoder().decode(it) }
            val key = ProfileCrypto.generateKey()
            prefs.edit().putString(KEY_OWN, Base64.getEncoder().encodeToString(key)).apply()
            return key
        }

        fun hasOwnProfileKey(): Boolean = prefs.contains(KEY_OWN)

        fun contactProfileKey(userId: String): ByteArray? =
            prefs.getString(contactKey(userId), null)?.let { Base64.getDecoder().decode(it) }

        /**
         * Stores [key] for [userId]. Returns true when it differs from what was
         * held before — the signal to re-resolve that contact's profile.
         */
        @Synchronized
        fun saveContactProfileKey(
            userId: String,
            key: ByteArray,
        ): Boolean {
            require(key.size == ProfileCrypto.KEY_SIZE) { "profile key must be ${ProfileCrypto.KEY_SIZE} bytes" }
            val existing = contactProfileKey(userId)
            if (existing != null && existing.contentEquals(key)) return false
            prefs.edit().putString(contactKey(userId), Base64.getEncoder().encodeToString(key)).apply()
            return true
        }

        /** Every user id we hold a Profile Key for — the set a cold-launch re-resolution walks. */
        fun contactUserIds(): List<String> =
            prefs.all.keys
                .filter { it.startsWith(KEY_CONTACT_PREFIX) }
                .map { it.removePrefix(KEY_CONTACT_PREFIX) }

        fun deleteContactProfileKey(userId: String) {
            prefs.edit().remove(contactKey(userId)).apply()
        }

        /** Wipes everything — part of account deletion / logout. */
        fun clear() {
            prefs.edit().clear().apply()
        }

        private fun contactKey(userId: String) = "$KEY_CONTACT_PREFIX$userId"

        private companion object {
            const val PREFS_NAME = "sanchr_profile_keys"
            const val KEY_OWN = "profile.key.own"
            const val KEY_CONTACT_PREFIX = "profile.key.contact."
        }
    }
