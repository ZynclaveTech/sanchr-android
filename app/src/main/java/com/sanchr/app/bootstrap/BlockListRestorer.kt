package com.sanchr.app.bootstrap

import android.util.Log
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.domain.contacts.ContactRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Brings the server's blocked list onto a new install, once.
 *
 * A block is enforced by this phone, from this phone's record: a sealed
 * message hides its sender from the server, so the server cannot refuse it.
 * That record starts empty. Reinstall, or move to a new phone, and everyone
 * who had been blocked could write again — while the server went on listing
 * them as blocked.
 *
 * Once per install, not at every launch. After the first time the phone's
 * record is the newer one: an unblock is written here first and sent to the
 * server afterwards, and if that send failed, taking the server's list again
 * would block them again.
 *
 * It only ever adds. A failure changes nothing and is tried again at the next
 * launch.
 */
@Singleton
class BlockListRestorer
    @Inject
    constructor(
        private val contactRepository: ContactRepository,
        private val userPreferences: UserPreferences,
    ) {
        /** @return whether this install now has the server's list, from this call or an earlier one. */
        suspend fun restoreOnce(): Boolean {
            if (userPreferences.blockListRestored.first()) return true
            return try {
                contactRepository.restoreBlockedFromServer()
                userPreferences.setBlockListRestored(true)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "could not restore the blocked list; will try again at the next launch", e)
                false
            }
        }

        private companion object {
            const val TAG = "BlockListRestorer"
        }
    }
