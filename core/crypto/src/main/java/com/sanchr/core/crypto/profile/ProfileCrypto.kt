package com.sanchr.core.crypto.profile

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Profile-field encryption, wire-compatible with iOS `ProfileCrypto.swift`.
 *
 * The server holds only ciphertext for display name, bio and avatar URL;
 * possession of the owner's 32-byte Profile Key, not any server-side ACL, is
 * what makes a profile readable. Each field is encrypted under its own
 * subkey so a ciphertext for one field cannot be replayed as another.
 *
 * Construction, byte for byte:
 * - subkey = HKDF-SHA256(ikm = profileKey, salt = none, info = [ProfileField.label]), 32 bytes
 * - AES-256-GCM, fresh random 12-byte nonce, 16-byte tag, no AAD
 * - blob = nonce ‖ ciphertext ‖ tag   (so blob.size == plaintext.size + 28)
 * - plaintext is UTF-8
 *
 * "No salt" is HKDF's default of HashLen zero bytes; for HMAC that is the
 * same key as the empty string. [version] is `SHA-256(profileKey)[0..16]`,
 * uploaded alongside the ciphertext so peers can tell stale copies apart.
 */
object ProfileCrypto {
    enum class ProfileField(
        internal val label: String,
    ) {
        DISPLAY_NAME("sanchr-profile-display-name-v1"),
        BIO("sanchr-profile-bio-v1"),
        AVATAR_URL("sanchr-profile-avatar-url-v1"),
    }

    const val KEY_SIZE = 32
    const val NONCE_SIZE = 12
    const val TAG_SIZE = 16
    private const val TAG_BITS = TAG_SIZE * 8
    const val VERSION_SIZE = 16

    private val random = SecureRandom()

    /** Encrypts [plaintext] for [field]; the empty string is a valid input and yields a 28-byte blob. */
    fun encryptField(
        plaintext: String,
        profileKey: ByteArray,
        field: ProfileField,
    ): ByteArray {
        requireKey(profileKey)
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveFieldKey(profileKey, field), "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val sealed = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)) // ciphertext ‖ tag
        return nonce + sealed
    }

    /**
     * Decrypts a blob produced by [encryptField] (on either platform).
     * @throws ProfileCryptoException on a wrong key, a tampered blob, a
     *   truncated blob, or non-UTF-8 plaintext.
     */
    fun decryptField(
        blob: ByteArray,
        profileKey: ByteArray,
        field: ProfileField,
    ): String {
        requireKey(profileKey)
        if (blob.size < NONCE_SIZE + TAG_SIZE) throw ProfileCryptoException("blob too short: ${blob.size} bytes")
        val nonce = blob.copyOfRange(0, NONCE_SIZE)
        val sealed = blob.copyOfRange(NONCE_SIZE, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveFieldKey(profileKey, field), "AES"), GCMParameterSpec(TAG_BITS, nonce))
        val plaintext =
            try {
                cipher.doFinal(sealed)
            } catch (e: javax.crypto.AEADBadTagException) {
                throw ProfileCryptoException("authentication failed: wrong key or tampered blob", e)
            }
        return String(plaintext, Charsets.UTF_8)
    }

    /** `SHA-256(profileKey)[0..16]` — what travels as `profile_key_version`. */
    fun version(profileKey: ByteArray): ByteArray {
        requireKey(profileKey)
        return MessageDigest.getInstance("SHA-256").digest(profileKey).copyOf(VERSION_SIZE)
    }

    fun generateKey(): ByteArray = ByteArray(KEY_SIZE).also(random::nextBytes)

    /** HKDF-SHA256 (RFC 5869), salt = HashLen zeros, one 32-byte output block. Internal for the KAT. */
    internal fun deriveFieldKey(
        profileKey: ByteArray,
        field: ProfileField,
    ): ByteArray =
        hkdfSha256(ikm = profileKey, salt = ByteArray(HASH_LEN), info = field.label.toByteArray(Charsets.UTF_8), length = KEY_SIZE)

    internal fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        require(length in 1..(255 * HASH_LEN))
        // RFC 5869: an absent salt is HashLen zero bytes. JCE refuses an empty
        // HMAC key outright, so normalise here rather than at each caller —
        // this is the spelling CryptoKit's salt-less derive resolves to.
        val prk = hmac(if (salt.isEmpty()) ByteArray(HASH_LEN) else salt, ikm)
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var filled = 0
        var counter = 1
        while (filled < length) {
            previous = hmac(prk, previous + info + byteArrayOf(counter.toByte()))
            val n = minOf(previous.size, length - filled)
            System.arraycopy(previous, 0, out, filled, n)
            filled += n
            counter++
        }
        return out
    }

    private fun hmac(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

    private fun requireKey(key: ByteArray) {
        if (key.size != KEY_SIZE) throw ProfileCryptoException("profileKey must be $KEY_SIZE bytes, got ${key.size}")
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val HASH_LEN = 32
}

class ProfileCryptoException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
