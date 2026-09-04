package com.sanchr.domain.messaging

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.Result
import com.sanchr.proto.auth.AuthServiceClient
import com.sanchr.proto.auth.DeleteAccountRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Permanently deletes the account, server-side first and locally second.
 *
 * Deletion is the only way out of Sanchr by design — there is deliberately
 * no sign-out — so this is the single exit path, and its ordering is
 * load-bearing:
 *
 * 1. [AuthServiceClient.deleteAccount] runs **first**, while the session is
 *    still valid. The RPC authenticates from the Bearer access token, so it
 *    cannot succeed once the local wipe has discarded that token.
 * 2. Only on success does [LogoutUseCase] run the local teardown (SQLCipher
 *    DB, Keystore-wrapped passphrase, staged identity, session prefs,
 *    delivery-token pool), which also flips `SessionManager.sessionActive`
 *    to `false` — the signal the UI observes to navigate back to auth.
 *
 * A failed server call therefore leaves the device untouched and returns
 * [Result.Error], so the user can retry. The reverse ordering is the
 * tempting one — it always looks like it worked — and it is exactly the bug
 * this ordering exists to prevent: a wiped device holding no credentials
 * for an account that still exists.
 *
 * Deregistering the push token stays with the caller, mirroring how the
 * logout path already handles it; `domain:messaging` does not depend on
 * `core:notifications`.
 */
@Singleton
class DeleteAccountUseCase
    @Inject
    constructor(
        private val authServiceClient: AuthServiceClient,
        private val logoutUseCase: LogoutUseCase,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend operator fun invoke(): Result<Unit> =
            withContext(dispatchers.io) {
                try {
                    authServiceClient.deleteAccount(DeleteAccountRequest())
                    logoutUseCase()
                    Result.Success(Unit)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Log.w(TAG, "account deletion failed; local state left intact", error)
                    Result.Error(error)
                }
            }

        private companion object {
            private const val TAG = "DeleteAccountUseCase"
        }
    }
