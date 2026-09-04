package com.sanchr.core.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import org.signal.libsignal.protocol.IdentityKeyPair

/**
 * Staged identity material captured before the account row exists.
 */
data class StagedIdentity(
    val keypair: IdentityKeyPair,
    val registrationId: Int,
)

/**
 * Persists the in-flight identity keypair + registration id to disk during
 * the registration window. Without this, a process crash between
 * [SanchrIdentityKeyStore.storeIdentityKeyPair] and the `accounts` row upsert
 * would permanently lose the generated identity — forcing the user back
 * through registration (and, worse, publishing a fresh identity key that
 * peers would see as a "safety-number change").
 *
 * Encryption: AES-256-GCM with a key held in the AndroidKeyStore.
 * `setUnlockedDeviceRequired(true)` is applied on API 28+, matching
 * [com.sanchr.core.database.crypto.DatabasePassphraseProvider].
 *
 * File layout on disk (`filesDir/.sanchr/staged-identity.bin`):
 *   [12 bytes IV][ciphertext: registrationId(4 BE) || identityKeyPair.serialize()]
 */
@Singleton
open class StagedIdentityStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val file: File by lazy {
            val dir = File(context.filesDir, SUBDIR).apply { if (!exists()) mkdirs() }
            File(dir, FILENAME)
        }

        /** Encrypts and persists [keypair] + [registrationId]. */
        @Synchronized
        open fun stage(
            keypair: IdentityKeyPair,
            registrationId: Int,
        ) {
            val serialized = keypair.serialize()
            val plaintext =
                ByteBuffer
                    .allocate(REGISTRATION_ID_BYTES + serialized.size)
                    .putInt(registrationId)
                    .put(serialized)
                    .array()
            file.writeBytes(wrap(plaintext))
        }

        /** Returns the currently staged identity, or null if nothing is staged. */
        @Synchronized
        open fun loadStaged(): StagedIdentity? {
            if (!file.exists()) return null
            val blob = runCatching { file.readBytes() }.getOrNull() ?: return null
            val plaintext =
                runCatching { unwrap(blob) }.getOrNull()
                    ?: return null.also { clear() }
            if (plaintext == null || plaintext.size <= REGISTRATION_ID_BYTES) return null
            val buf = ByteBuffer.wrap(plaintext)
            val registrationId = buf.int
            val keyBytes = ByteArray(plaintext.size - REGISTRATION_ID_BYTES)
            buf.get(keyBytes)
            return StagedIdentity(
                keypair = IdentityKeyPair(keyBytes),
                registrationId = registrationId,
            )
        }

        /** Deletes the staged blob and the backing AndroidKeyStore entry. */
        @Synchronized
        open fun clear() {
            runCatching { if (file.exists()) file.delete() }
            runCatching {
                KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
            }
        }

        /**
         * AES-GCM-wraps [plaintext] with the AndroidKeyStore-held master key.
         * Layout: `[12 bytes IV][ciphertext + GCM tag]`. Overridable so tests
         * can bypass the AndroidKeyStore on JVM runners that lack shadows for
         * `AES/GCM/NoPadding`.
         */
        protected open fun wrap(plaintext: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            check(iv.size == GCM_IV_BYTES) { "Unexpected GCM IV length: ${iv.size}" }
            val ciphertext = cipher.doFinal(plaintext)
            val out = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, out, 0, iv.size)
            System.arraycopy(ciphertext, 0, out, iv.size, ciphertext.size)
            return out
        }

        /**
         * Inverse of [wrap]. Returns `null` if the key has been rotated/deleted
         * (stale blob) so the caller can drop the file.
         */
        protected open fun unwrap(blob: ByteArray): ByteArray? {
            if (blob.size <= GCM_IV_BYTES) return null
            val iv = blob.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = blob.copyOfRange(GCM_IV_BYTES, blob.size)
            val secretKey = getExistingKey() ?: return null
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(ciphertext)
        }

        private fun getOrCreateKey(): SecretKey {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            return generateKey(unlockedDeviceRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        }

        /**
         * `setUnlockedDeviceRequired` is defence in depth, and some KeyMint
         * implementations reject it outright with `SYSTEM_ERROR` (the API 34
         * x86_64 emulator does). Retry without it rather than fail
         * registration; a Keystore that cannot generate a plain AES-GCM key
         * is a real failure and is allowed to throw. Same ladder as
         * `DatabasePassphraseProvider`.
         */
        private fun generateKey(unlockedDeviceRequired: Boolean): SecretKey {
            val builder =
                KeyGenParameterSpec
                    .Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_KEY_BITS)
                    .setRandomizedEncryptionRequired(true)
            if (unlockedDeviceRequired) {
                builder.setUnlockedDeviceRequired(true)
            }

            val generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            return try {
                generator.init(builder.build())
                generator.generateKey()
            } catch (e: Exception) {
                if (!unlockedDeviceRequired) throw e
                Log.w(TAG, "Keystore refused unlockedDeviceRequired ($e); retrying staged-identity key without it")
                generateKey(unlockedDeviceRequired = false)
            }
        }

        private fun getExistingKey(): SecretKey? {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val entry = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry ?: return null
            return entry.secretKey
        }

        private companion object {
            const val TAG = "StagedIdentity"
            const val SUBDIR = ".sanchr"
            const val FILENAME = "staged-identity.bin"
            const val KEY_ALIAS = "sanchr-staged-identity-v1"
            const val ANDROID_KEYSTORE = "AndroidKeyStore"
            const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
            const val AES_KEY_BITS = 256
            const val GCM_IV_BYTES = 12
            const val GCM_TAG_BITS = 128
            const val REGISTRATION_ID_BYTES = 4
        }
    }
