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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface RefreshResult {
    data object Refreshed : RefreshResult

    data object NoSession : RefreshResult

    data object Transient : RefreshResult

    data object SignedOut : RefreshResult
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
                    val rotated = response.refreshToken.ifEmpty { refreshToken }
                    sessionManager.updateTokens(response.accessToken, rotated, clock() + response.expiresIn * 1000L)
                    RefreshResult.Refreshed
                } catch (e: Exception) {
                    when (statusOf(e)) {
                        Status.Code.UNAUTHENTICATED, Status.Code.PERMISSION_DENIED -> {
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
