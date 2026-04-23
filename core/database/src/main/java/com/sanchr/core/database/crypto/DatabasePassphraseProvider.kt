package com.sanchr.core.database.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the SQLCipher database passphrase.
 *
 * The passphrase is a 256-bit random value generated once on first launch. It is
 * AES-GCM-wrapped with a key held in the AndroidKeyStore (StrongBox preferred,
 * TEE fallback), and the wrapped blob is stored in [EncryptedSharedPreferences].
 */
@Singleton
class DatabasePassphraseProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs by lazy {
            val masterKey =
                MasterKey
                    .Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        /**
         * Returns the database passphrase, generating + persisting one on first call.
         */
        @Synchronized
        fun obtainPassphrase(): ByteArray {
            val wrapped = prefs.getString(KEY_WRAPPED_PASSPHRASE, null)
            val iv = prefs.getString(KEY_WRAP_IV, null)
            return if (wrapped != null && iv != null) {
                unwrap(
                    Base64.decode(wrapped, Base64.NO_WRAP),
                    Base64.decode(iv, Base64.NO_WRAP),
                )
            } else {
                generateAndStore()
            }
        }

        /**
         * Clears stored passphrase material. Destructive: the existing encrypted
         * database becomes unreadable after this call.
         */
        fun wipe() {
            prefs.edit().clear().apply()
            runCatching {
                KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
            }
        }

        private fun generateAndStore(): ByteArray {
            val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
            val iv = cipher.iv
            val wrapped = cipher.doFinal(passphrase)
            prefs
                .edit()
                .putString(KEY_WRAPPED_PASSPHRASE, Base64.encodeToString(wrapped, Base64.NO_WRAP))
                .putString(KEY_WRAP_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()
            return passphrase
        }

        private fun unwrap(
            wrapped: ByteArray,
            iv: ByteArray,
        ): ByteArray {
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateWrappingKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            return cipher.doFinal(wrapped)
        }

        private fun getOrCreateWrappingKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
                return it.secretKey
            }
            return generateWrappingKey(useStrongBox = true)
        }

        private fun generateWrappingKey(useStrongBox: Boolean): SecretKey {
            val builder =
                KeyGenParameterSpec
                    .Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_KEY_BITS)
                    .setRandomizedEncryptionRequired(true)

            val strongBoxRequested =
                useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setUnlockedDeviceRequired(true)
                if (strongBoxRequested) {
                    builder.setIsStrongBoxBacked(true)
                }
            }

            val generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            return try {
                generator.init(builder.build())
                generator.generateKey()
            } catch (e: Exception) {
                if (strongBoxRequested && isStrongBoxUnavailable(e)) {
                    generateWrappingKey(useStrongBox = false)
                } else {
                    throw e
                }
            }
        }

        private fun isStrongBoxUnavailable(e: Throwable): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return e is StrongBoxUnavailableException ||
                generateSequence(e.cause) { it.cause }.any { it is StrongBoxUnavailableException }
        }

        private companion object {
            const val PREFS_FILE = "sanchr-db-secrets"
            const val KEY_WRAPPED_PASSPHRASE = "wrapped_passphrase"
            const val KEY_WRAP_IV = "wrap_iv"
            const val KEY_ALIAS = "sanchr-db-passphrase-wrap-v1"
            const val ANDROID_KEYSTORE = "AndroidKeyStore"
            const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
            const val AES_KEY_BITS = 256
            const val GCM_TAG_BITS = 128
            const val PASSPHRASE_BYTES = 32
        }
    }
