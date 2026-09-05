package com.sanchr.core.common.lock

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockPolicyTest {
    @Test
    fun `either switch configures the lock, as a timeout without biometrics is still a lock`() {
        assertTrue(AppLockPolicy.isConfigured(screenLockEnabled = true, biometricEnabled = false))
        assertTrue(AppLockPolicy.isConfigured(screenLockEnabled = false, biometricEnabled = true))
        assertTrue(AppLockPolicy.isConfigured(screenLockEnabled = true, biometricEnabled = true))
        assertFalse(AppLockPolicy.isConfigured(screenLockEnabled = false, biometricEnabled = false))
    }

    @Test
    fun `the grace period is honoured, and zero locks the moment the app leaves`() {
        val away = { seconds: Long, timeout: Int ->
            AppLockPolicy.shouldLockOnResume(
                configured = true,
                backgroundedAtMillis = 1_000_000,
                nowMillis = 1_000_000 + seconds * 1000,
                timeoutSeconds = timeout,
            )
        }
        assertFalse(away(29, 30))
        assertTrue(away(30, 30))
        assertTrue(away(31, 30))
        assertTrue(away(0, 0))
    }

    @Test
    fun `nothing locks when the lock is off, when the app never left, or when the clock jumps back`() {
        assertFalse(
            AppLockPolicy.shouldLockOnResume(configured = false, backgroundedAtMillis = 0, nowMillis = Long.MAX_VALUE, timeoutSeconds = 0),
        )
        assertFalse(AppLockPolicy.shouldLockOnResume(configured = true, backgroundedAtMillis = null, nowMillis = 5_000, timeoutSeconds = 0))
        // Clock moved backwards while away: treated as no time passed, not as a huge negative.
        assertFalse(
            AppLockPolicy.shouldLockOnResume(configured = true, backgroundedAtMillis = 9_000, nowMillis = 1_000, timeoutSeconds = 30),
        )
    }
}
