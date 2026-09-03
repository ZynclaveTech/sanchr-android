package com.sanchr.core.network

import android.util.Log
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.auth.AuthServiceClient
import com.sanchr.proto.auth.RefreshTokenRequest
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface RefreshResult {
    data object Refreshed : RefreshResult

    data object NoSession : RefreshResult

    data object Transient : RefreshResult

    data object SignedOut : RefreshResult

    /**
     * The RPC succeeded, but the session this refresh started with is no
     * longer the one in storage — most likely a concurrent logout cleared it
     * while the call was in flight. The rotated pair is discarded rather
     * than written back, so this is honestly "not refreshed": callers must
     * not treat it as [Refreshed].
     */
    data object SessionChanged : RefreshResult
}

/**
 * Exchanges the refresh token for a new pair, once at a time. Every caller
 * that arrives while a refresh is in flight (or that arrives right after one
 * just finished) shares that refresh's result instead of spending another
 * RPC: the backend detects refresh-token reuse per family, so two concurrent
 * refreshes from one device would sign the whole account out.
 */
@Singleton
class SessionRefresher(
    private val authClient: AuthServiceClient,
    private val sessionManager: SessionManager,
    private val clock: () -> Long,
) {
    @Inject
    constructor(authClient: AuthServiceClient, sessionManager: SessionManager) :
        this(authClient, sessionManager, System::currentTimeMillis)

    private val mutex = Mutex()
    private var lastOutcome: Pair<Long, RefreshResult>? = null

    suspend fun refresh(): RefreshResult =
        mutex.withLock {
            // A caller queued behind a refresh that just finished gets that
            // outcome instead of spending the freshly minted token again.
            lastOutcome?.let { (at, result) -> if (clock() - at < REUSE_WINDOW_MS) return result }
            val refreshToken = sessionManager.getRefreshToken() ?: return RefreshResult.NoSession
            val result =
                try {
                    val response = authClient.refreshToken(RefreshTokenRequest(refreshToken = refreshToken))
                    if (sessionManager.getRefreshToken() != refreshToken) {
                        // Something else (logout, most plausibly) replaced or
                        // cleared the session while this RPC was in flight.
                        // Writing the rotated pair back now would resurrect a
                        // session that was already signed out from under the
                        // user — discard it instead.
                        Log.w(TAG, "Session changed during refresh; discarding rotated tokens")
                        RefreshResult.SessionChanged
                    } else {
                        val rotated = response.refreshToken.ifEmpty { refreshToken }
                        sessionManager.updateTokens(response.accessToken, rotated, clock() + response.expiresIn * 1000L)
                        RefreshResult.Refreshed
                    }
                } catch (e: CancellationException) {
                    // Structured concurrency: a cancelled scope must propagate,
                    // never be absorbed as a refresh outcome.
                    throw e
                } catch (e: Exception) {
                    when (statusOf(e)) {
                        // Only an explicit UNAUTHENTICATED means the refresh
                        // token itself was rejected. Anything else (including
                        // PERMISSION_DENIED, which the refresh endpoint never
                        // returns today) is treated as transient rather than
                        // signing every device out — a wrong destructive
                        // sign-out is worse than a retry.
                        Status.Code.UNAUTHENTICATED -> {
                            Log.w(TAG, "Refresh token rejected; signing out", e)
                            sessionManager.clearSession()
                            RefreshResult.SignedOut
                        }
                        else -> {
                            Log.w(TAG, "Refresh failed transiently", e)
                            RefreshResult.Transient
                        }
                    }
                }
            lastOutcome = clock() to result
            result
        }

    private fun statusOf(e: Exception): Status.Code? =
        when (e) {
            is StatusException -> e.status.code
            is StatusRuntimeException -> e.status.code
            else -> null
        }

    companion object {
        private const val TAG = "SessionRefresher"
        private const val REUSE_WINDOW_MS = 2_000L
    }
}
