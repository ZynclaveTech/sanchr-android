package com.sanchr.core.network

import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.auth.AuthResponse
import com.sanchr.proto.auth.AuthServiceClient
import com.sanchr.proto.auth.RefreshTokenRequest
import io.grpc.Status
import io.grpc.StatusException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRefresherTest {
    private val session = mockk<SessionManager>(relaxed = true)
    private val auth = mockk<AuthServiceClient>()
    private val refresher = SessionRefresher(auth, session, clock = { 1_000_000L })

    @Test
    fun `stores the rotated pair and the expiry`() =
        runTest {
            every { session.getRefreshToken() } returns "r1"
            coEvery { auth.refreshToken(RefreshTokenRequest("r1")) } returns
                AuthResponse(accessToken = "a2", refreshToken = "r2", expiresIn = 900)

            assertEquals(RefreshResult.Refreshed, refresher.refresh())
            verify { session.updateTokens("a2", "r2", 1_000_000L + 900_000L) }
        }

    @Test
    fun `concurrent callers share one RPC`() =
        runTest {
            every { session.getRefreshToken() } returns "r1"
            coEvery { auth.refreshToken(any()) } returns AuthResponse(accessToken = "a2", refreshToken = "r2", expiresIn = 900)

            listOf(async { refresher.refresh() }, async { refresher.refresh() }, async { refresher.refresh() }).awaitAll()

            coVerify(exactly = 1) { auth.refreshToken(any()) }
        }

    @Test
    fun `an unauthenticated refresh signs the session out`() =
        runTest {
            every { session.getRefreshToken() } returns "r1"
            coEvery { auth.refreshToken(any()) } throws StatusException(Status.UNAUTHENTICATED)

            assertEquals(RefreshResult.SignedOut, refresher.refresh())
            verify { session.clearSession() }
        }

    @Test
    fun `a network failure keeps the session`() =
        runTest {
            every { session.getRefreshToken() } returns "r1"
            coEvery { auth.refreshToken(any()) } throws StatusException(Status.UNAVAILABLE)

            assertEquals(RefreshResult.Transient, refresher.refresh())
            verify(exactly = 0) { session.clearSession() }
        }

    @Test
    fun `no refresh token means no session`() =
        runTest {
            every { session.getRefreshToken() } returns null
            assertEquals(RefreshResult.NoSession, refresher.refresh())
        }
}
