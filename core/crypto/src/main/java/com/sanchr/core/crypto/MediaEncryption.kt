package com.sanchr.core.crypto

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles AES-256-GCM encryption/decryption for media files (images, videos, documents).
 * Each file gets a unique random key; the key is then encrypted with the Signal session.
 */
@Singleton
class MediaEncryption @Inject constructor() {

    private companion object {
        const val ALGORITHM = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
    }

    /**
     * Generates a random AES-256 key for encrypting a single media file.
     *
     * @return The raw key bytes (32 bytes).
     */
    fun generateMediaKey(): ByteArray {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(KEY_SIZE_BITS, SecureRandom())
        return keyGenerator.generateKey().encoded
    }

    /**
     * Encrypts data in-memory using AES-256-GCM.
     *
     * @param plaintext The data to encrypt.
     * @param key The 32-byte AES key.
     * @return IV prepended to ciphertext (12 + N bytes).
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    /**
     * Decrypts data that was encrypted with [encrypt].
     *
     * @param data IV-prepended ciphertext (12 + N bytes).
     * @param key The 32-byte AES key.
     * @return The decrypted plaintext.
     */
    fun decrypt(data: ByteArray, key: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, IV_SIZE_BYTES)
        val ciphertext = data.copyOfRange(IV_SIZE_BYTES, data.size)
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Encrypts a stream for large files. Writes IV first, then encrypted data.
     *
     * @param input Source plaintext stream.
     * @param output Destination encrypted stream.
     * @param key The 32-byte AES key.
     */
    fun encryptStream(input: InputStream, output: OutputStream, key: ByteArray) {
        val iv = ByteArray(IV_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE_BITS, iv))

        output.write(iv)

        CipherOutputStream(output, cipher).use { cipherOut ->
            input.copyTo(cipherOut, bufferSize = 8192)
        }
    }

    /**
     * Decrypts a stream that was encrypted with [encryptStream].
     *
     * @param input Source encrypted stream (IV + ciphertext).
     * @param output Destination plaintext stream.
     * @param key The 32-byte AES key.
     */
    fun decryptStream(input: InputStream, output: OutputStream, key: ByteArray) {
        val iv = ByteArray(IV_SIZE_BYTES)
        input.read(iv)

        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE_BITS, iv))

        CipherInputStream(input, cipher).use { cipherIn ->
            cipherIn.copyTo(output, bufferSize = 8192)
        }
    }
}
