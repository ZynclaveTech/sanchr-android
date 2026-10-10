package com.sanchr.feature.settings

import com.sanchr.core.crypto.store.SignedPreKeyInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the Encryption Keys screen is allowed to claim.
 *
 * It used to print "Active" in green beside a literal "Key ID: SPK-0001",
 * "Generated: --" and "-- keys remaining on server" — fabricated security
 * state, on the one screen whose purpose is to report the real thing.
 */
class EncryptionKeysStateTest {
    @Test
    fun `active means a signed pre-key actually exists`() {
        val withKey =
            EncryptionKeysViewModel.KeyState(
                signedPreKey = SignedPreKeyInfo(id = 7, createdAtMillis = 1_700_000_000_000),
                remainingPreKeys = 42,
                loaded = true,
            )

        assertTrue(withKey.isActive)
        assertEquals(7, withKey.signedPreKey?.id)
    }

    @Test
    fun `no signed pre-key is not active`() {
        val state = EncryptionKeysViewModel.KeyState(signedPreKey = null, remainingPreKeys = 0, loaded = true)

        assertFalse(state.isActive)
    }

    @Test
    fun `before anything is read, nothing is claimed`() {
        val initial = EncryptionKeysViewModel.KeyState()

        assertFalse(initial.loaded)
        assertFalse(initial.isActive)
        // Negative, so the screen can say the count could not be read rather
        // than showing a plausible zero.
        assertEquals(EncryptionKeysViewModel.UNKNOWN_COUNT, initial.remainingPreKeys)
        assertTrue(initial.remainingPreKeys < 0)
    }
}
