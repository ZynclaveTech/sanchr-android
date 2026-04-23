package com.sanchr.core.notifications

/**
 * Indirection used by [SanchrPushService] to enqueue the FCM drain worker
 * without pulling the `sync` module into `core:notifications` (which would
 * be a dependency cycle — `sync` already depends on `core:notifications`).
 *
 * The implementation lives in the `sync` module and is bound via Hilt.
 */
interface MessageDrainScheduler {
    /** Enqueue a unique, expedited drain. Collapses onto any in-flight drain. */
    fun enqueueDrain()
}
