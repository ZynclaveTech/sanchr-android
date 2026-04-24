package com.sanchr.domain.messaging

import android.content.Context
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.StagedIdentityStore
import com.sanchr.core.database.SanchrDatabase
import com.sanchr.core.database.crypto.DatabasePassphraseProvider
import com.sanchr.core.datastore.SessionManager
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
 * password, display name), and the AndroidKeyStore-wrapped database
 * passphrase.
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
        private val database: SanchrDatabase,
        private val stagedIdentityStore: StagedIdentityStore,
        private val databasePassphraseProvider: DatabasePassphraseProvider,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend operator fun invoke() =
            withContext(dispatchers.io) {
                // 1. Clear in-flight staged identity (if any)
                runCatching { stagedIdentityStore.clear() }
                // 2. Close SQLCipher DB so its file handles release
                runCatching { database.close() }
                // 3. Delete DB file — includes journal & wal
                runCatching { context.deleteDatabase(DATABASE_FILE_NAME) }
                // 4. Clear encrypted session prefs (tokens, password, display name)
                runCatching { sessionManager.clearSession() }
                // 5. Wipe Keystore-wrapped DB passphrase
                runCatching { databasePassphraseProvider.wipe() }
            }

        private companion object {
            /** Matches the literal passed to `Room.databaseBuilder` in `SanchrDatabase`. */
            const val DATABASE_FILE_NAME = "sanchr-database"
        }
    }
