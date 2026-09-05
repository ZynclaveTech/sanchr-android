package com.sanchr.core.common.lock

/**
 * When App Lock demands authentication. Kept apart from the prompt itself so
 * the rule is testable without a device.
 *
 * The lock is configured when *either* switch is on, as iOS: a lock set up as
 * a timeout without biometrics is still a lock, and iOS's gate used to check
 * only the biometric flag and so never locked in that case.
 */
object AppLockPolicy {
    /** Whether the user has App Lock set up at all. */
    fun isConfigured(
        screenLockEnabled: Boolean,
        biometricEnabled: Boolean,
    ): Boolean = screenLockEnabled || biometricEnabled

    /**
     * Whether returning to the foreground must re-authenticate.
     *
     * @param backgroundedAtMillis when the app last went to the background, or
     *   null when it has not since being unlocked (so nothing has elapsed).
     * @param timeoutSeconds grace period; 0 locks immediately.
     */
    fun shouldLockOnResume(
        configured: Boolean,
        backgroundedAtMillis: Long?,
        nowMillis: Long,
        timeoutSeconds: Int,
    ): Boolean {
        if (!configured) return false
        if (backgroundedAtMillis == null) return false
        // A clock that moved backwards (time zone, NTP) must not extend the grace period.
        val awayMillis = (nowMillis - backgroundedAtMillis).coerceAtLeast(0)
        return awayMillis >= timeoutSeconds * MILLIS_PER_SECOND
    }

    private const val MILLIS_PER_SECOND = 1000L
}
