package com.sanchr.domain.messaging

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.sanchr.core.crypto.profile.ProfileKeyStore
import com.sanchr.core.crypto.store.SanchrKyberPreKeyStore
import com.sanchr.core.crypto.store.SanchrSenderKeyStore
import com.sanchr.core.datastore.NotifierState
import com.sanchr.domain.messaging.media.MediaCacheCleaner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * What an account leaves on this phone outside its database, and the removal
 * of it.
 *
 * Deleting an account closed and deleted the database, cleared the session
 * and the preferences, and stopped there. Left behind, readable by the next
 * person to use the app on this phone, or by anyone with the phone:
 *
 *  - every photo, video, document and voice note that had been opened, in
 *    the clear, in the cache directory — along with link-preview images and
 *    anything previewed from the vault;
 *  - the post-quantum pre-keys, the record of which had been used, and group
 *    sender keys, in the files directory;
 *  - this account's Profile Key and every contact's, which open their
 *    names and photos;
 *  - notifications still showing who had written.
 *
 * iOS removes the equivalents. The confirmation screen says everything goes.
 *
 * Each item is attempted whatever happened to the one before: a wipe that
 * stops at the first failure leaves more behind than one that carries on.
 */
@Singleton
class AccountLeftovers
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val mediaCache: MediaCacheCleaner,
        private val kyberPreKeys: SanchrKyberPreKeyStore,
        private val senderKeys: SanchrSenderKeyStore,
        private val profileKeys: ProfileKeyStore,
        private val notifierState: NotifierState,
    ) {
        suspend fun wipe() {
            attempt("decrypted media cache") { mediaCache.clear() }
            attempt("kyber pre-keys") { kyberPreKeys.wipeAll() }
            attempt("sender keys") { senderKeys.wipeAll() }
            attempt("profile keys") { profileKeys.clear() }
            attempt("notifier state") { notifierState.clear() }
            attempt("notifications") {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancelAll()
            }
        }

        private suspend fun attempt(
            what: String,
            remove: suspend () -> Unit,
        ) {
            try {
                remove()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Named, never silent: something of the account is still here.
                Log.e(TAG, "could not remove $what while wiping the account", e)
            }
        }

        private companion object {
            const val TAG = "AccountLeftovers"
        }
    }
