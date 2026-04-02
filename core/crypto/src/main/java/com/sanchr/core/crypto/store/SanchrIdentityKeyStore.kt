package com.sanchr.core.crypto.store

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.IdentityKeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent identity key store backed by EncryptedSharedPreferences.
 * The local identity key pair and registration ID are stored encrypted at rest
 * using Android Keystore AES-256-GCM via the Jetpack Security library.
 *
 * Remote identity keys (one per SignalProtocolAddress) are cached in memory
 * and persisted to encrypted prefs so they survive process death.
 */
@Singleton
class SanchrIdentityKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : IdentityKeyStore {

    private companion object {
        const val PREFS_NAME = "sanchr_identity_store"
        const val KEY_IDENTITY_KEY_PAIR = "identity_key_pair"
        const val KEY_LOCAL_REGISTRATION_ID = "local_registration_id"
        const val KEY_REMOTE_IDENTITY_PREFIX = "remote_identity_"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** In-memory cache of remote identity keys to avoid repeated disk reads. */
    private val remoteIdentityCache = ConcurrentHashMap<String, IdentityKey>()

    // ------------------------------------------------------------------
    // Local identity
    // ------------------------------------------------------------------

    override fun getIdentityKeyPair(): IdentityKeyPair {
        val encoded = prefs.getString(KEY_IDENTITY_KEY_PAIR, null)
            ?: throw IllegalStateException("Identity key pair has not been generated yet")
        return IdentityKeyPair(Base64.decode(encoded, Base64.NO_WRAP))
    }

    override fun getLocalRegistrationId(): Int {
        val regId = prefs.getInt(KEY_LOCAL_REGISTRATION_ID, -1)
        if (regId == -1) {
            throw IllegalStateException("Local registration ID has not been set yet")
        }
        return regId
    }

    /**
     * Persists the identity key pair generated during registration.
     * Must be called exactly once, before any Signal session work.
     */
    fun storeIdentityKeyPair(identityKeyPair: IdentityKeyPair) {
        val encoded = Base64.encodeToString(identityKeyPair.serialize(), Base64.NO_WRAP)
        prefs.edit().putString(KEY_IDENTITY_KEY_PAIR, encoded).apply()
    }

    /**
     * Persists the local registration ID generated during registration.
     */
    fun storeLocalRegistrationId(registrationId: Int) {
        prefs.edit().putInt(KEY_LOCAL_REGISTRATION_ID, registrationId).apply()
    }

    /**
     * Returns true if a local identity key pair has been generated and stored.
     */
    fun hasIdentityKeyPair(): Boolean =
        prefs.contains(KEY_IDENTITY_KEY_PAIR)

    // ------------------------------------------------------------------
    // Remote identities
    // ------------------------------------------------------------------

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
    ): Boolean {
        val key = addressKey(address)
        val existing = loadRemoteIdentity(key)
        val replaced = existing != null && existing != identityKey

        remoteIdentityCache[key] = identityKey
        val encoded = Base64.encodeToString(identityKey.serialize(), Base64.NO_WRAP)
        prefs.edit().putString(KEY_REMOTE_IDENTITY_PREFIX + key, encoded).apply()

        return replaced
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean {
        val key = addressKey(address)
        val stored = loadRemoteIdentity(key)
        // Trust-on-first-use (TOFU): if we have never seen this address, trust it.
        // If we have, verify it matches the stored key.
        return stored == null || stored == identityKey
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        return loadRemoteIdentity(addressKey(address))
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun addressKey(address: SignalProtocolAddress): String =
        "${address.name}_${address.deviceId}"

    private fun loadRemoteIdentity(key: String): IdentityKey? {
        remoteIdentityCache[key]?.let { return it }

        val encoded = prefs.getString(KEY_REMOTE_IDENTITY_PREFIX + key, null) ?: return null
        val identityKey = IdentityKey(Base64.decode(encoded, Base64.NO_WRAP))
        remoteIdentityCache[key] = identityKey
        return identityKey
    }

    /**
     * Wipes all identity data. Called on account deletion.
     */
    fun wipeAll() {
        remoteIdentityCache.clear()
        prefs.edit().clear().apply()
    }
}
