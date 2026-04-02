package com.sanchr.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages cryptographic keys including identity keys, pre-keys, and signed pre-keys.
 * Uses Android Keystore for hardware-backed key storage where available.
 */
@Singleton
class KeyManager @Inject constructor() {

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val LOCAL_IDENTITY_KEY_ALIAS = "sanchr_identity_key"
        const val PRE_KEY_BATCH_SIZE = 100
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    /**
     * Generates and stores the local identity key pair in Android Keystore.
     * Called once during initial registration.
     */
    fun generateIdentityKeyPair() {
        if (keyStore.containsAlias(LOCAL_IDENTITY_KEY_ALIAS)) return

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE,
        )

        val spec = KeyGenParameterSpec.Builder(
            LOCAL_IDENTITY_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setUserAuthenticationRequired(false)
            .build()

        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }

    /**
     * Returns the public key fingerprint for the local identity key.
     */
    fun getLocalIdentityFingerprint(): String {
        // TODO: Load public key from Keystore and compute fingerprint
        return ""
    }

    /**
     * Generates a batch of one-time pre-keys for upload to the server.
     *
     * @param startId The starting pre-key ID.
     * @return List of serialized pre-key pairs (id, publicKey).
     */
    suspend fun generatePreKeys(startId: Int): List<Pair<Int, ByteArray>> {
        // TODO: Generate PRE_KEY_BATCH_SIZE Curve25519 key pairs
        //   Store private keys locally, return public keys for server upload
        return emptyList()
    }

    /**
     * Generates a new signed pre-key, signed with the identity key.
     *
     * @param signedPreKeyId The ID for the new signed pre-key.
     * @return Serialized signed pre-key (id, publicKey, signature).
     */
    suspend fun generateSignedPreKey(signedPreKeyId: Int): Triple<Int, ByteArray, ByteArray> {
        // TODO: Generate Curve25519 key pair, sign public key with identity key
        return Triple(signedPreKeyId, ByteArray(0), ByteArray(0))
    }

    /**
     * Checks whether the identity key has been generated.
     */
    fun hasIdentityKey(): Boolean = keyStore.containsAlias(LOCAL_IDENTITY_KEY_ALIAS)

    /**
     * Wipes all cryptographic material. Called on account deletion.
     */
    fun wipeAllKeys() {
        keyStore.deleteEntry(LOCAL_IDENTITY_KEY_ALIAS)
        // TODO: Clear all pre-keys and signed pre-keys from local storage
    }
}
