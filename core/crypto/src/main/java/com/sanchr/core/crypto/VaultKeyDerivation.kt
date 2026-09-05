package com.sanchr.core.crypto

/**
 * `AccessK_vault` for a manually uploaded vault item, exactly as iOS
 * `MediaKeyDerivation.deriveVaultAccessKeyManual`:
 * `HKDF-SHA256(ikm = device media-access secret, salt = random 32 bytes,
 * info = "sanchr-vault-manual-v1-<vaultItemId>")`, 32 bytes.
 *
 * The key never leaves the device: the server stores only what was
 * encrypted under it, and a different device (or a reinstall) cannot
 * derive it — those items are "sealed", as on iOS.
 */
object VaultKeyDerivation {
    const val KEY_SIZE = 32
    const val SALT_SIZE = 32
    private const val LABEL_MANUAL = "sanchr-vault-manual-v1"

    fun deriveManual(
        deviceSecret: ByteArray,
        salt: ByteArray,
        vaultItemId: String,
    ): ByteArray {
        require(salt.size == SALT_SIZE) { "salt must be $SALT_SIZE bytes" }
        return Hkdf.sha256(deviceSecret, salt, "$LABEL_MANUAL-$vaultItemId".toByteArray(Charsets.UTF_8), KEY_SIZE)
    }
}
