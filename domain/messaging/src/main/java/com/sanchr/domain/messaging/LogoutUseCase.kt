package com.sanchr.domain.messaging

import android.content.Context
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.StagedIdentityStore
import com.sanchr.core.database.SanchrDatabase
import com.sanchr.core.database.crypto.DatabasePassphraseProvider
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Centralized "log this user out" workflow.
 *
 * Responsible for fully wiping every piece of local state tied to the
 * currently authenticated account: in-flight staged identity material,
 * the SQLCipher message database (closed then deleted on-disk, including
 * journal + WAL files), encrypted session preferences (tokens, account
 * password, display name), user preferences (onboarding flag, privacy
 * toggles), and the AndroidKeyStore-wrapped database passphrase.
 *
 * Each step is wrapped in [runCatching] so that a failure in an earlier
 * step does not prevent the remaining steps from running. In particular
 * [SessionManager.clearSession] always runs, which flips
 * [SessionManager.sessionActive] to `false` — that is the signal the UI
 * layer listens to for reactive navigation back to the auth graph. As
 * long as `clearSession` itself completes, logout navigation happens
 * even if e.g. a keystore wipe raced with another process.
 */
@Singleton
class LogoutUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sessionManager: SessionManager,
        private val userPreferences: UserPreferences,
        private val database: SanchrDatabase,
        private val stagedIdentityStore: StagedIdentityStore,
        private val databasePassphraseProvider: DatabasePassphraseProvider,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend operator fun invoke() =
            withContext(dispatchers.io) {
                // 1. Flip sessionActive → false first so the UI navigates away and
                //    cancels any ViewModel coroutines that are collecting DAO Flows.
                //    All subsequent steps run after nav teardown has been signalled,
                //    so the DB is never closed while live collectors are still active.
                runCatching { sessionManager.clearSession() }
                // 2. Wipe UserPreferences (onboarding flag, privacy toggles, etc.)
                //    so a second account logging in on the same device does not
                //    inherit the previous user's per-user preference state —
                //    most importantly `has_completed_onboarding`, which would
                //    otherwise skip onboarding for the new user.
                runCatching { userPreferences.clear() }
                // 3. Clear in-flight staged identity (if any)
                runCatching { stagedIdentityStore.clear() }
                // 4. Close SQLCipher DB so its file handles release
                runCatching { database.close() }
                // 5. Delete DB file — includes journal & wal
                runCatching { context.deleteDatabase(DATABASE_FILE_NAME) }
                // 6. Wipe Keystore-wrapped DB passphrase
                runCatching { databasePassphraseProvider.wipe() }
            }

        private companion object {
            /** Matches the literal passed to `Room.databaseBuilder` in `SanchrDatabase`. */
            const val DATABASE_FILE_NAME = "sanchr-database"
        }
    }
