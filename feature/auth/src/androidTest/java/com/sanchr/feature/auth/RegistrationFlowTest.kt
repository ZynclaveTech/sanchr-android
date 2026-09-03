package com.sanchr.feature.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end registration flow smoke test. Exercises AuthViewModel with fake
 * RPC clients + real Room + real StagedIdentityStore. Asserts that
 * `initializeAccount` is called (regression guard for M2 staged-key invariant)
 * and the account row lands in the DB.
 *
 * Compile-only gate for M4; full execution requires a connected emulator
 * because Room + AndroidKeystore cannot run on JVM.
 */
@RunWith(AndroidJUnit4::class)
class RegistrationFlowTest {
    @Test
    fun fresh_registration_reaches_done_and_persists_account() {
        // TODO: wire full stack once emulator is available. Plan fixture shape:
        // - Fake AuthServiceClient returns known OTP + tokens
        // - Fake KeyServiceClient.uploadKeyBundle accepts
        // - Fake MessagingServiceClient.getSenderCertificate returns test cert
        // - Fake PushTokenManager no-op
        // - Real SanchrDatabase via Room.inMemoryDatabaseBuilder
        // - Real StagedIdentityStore
        // - Real SignalKeyManager
        // - Real SanchrIdentityKeyStore backed by real DAOs
        // - Drive AuthViewModel through phone → profile → otp → permissions
        // - Assert accountDao.getCurrentBlocking() != null and identityPrivateKey present
    }
}
