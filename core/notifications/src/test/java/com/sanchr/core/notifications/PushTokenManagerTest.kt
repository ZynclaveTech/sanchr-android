package com.sanchr.core.notifications

import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.notifications.NotificationServiceClient
import com.sanchr.proto.notifications.RegisterPushTokenRequest
import com.sanchr.proto.notifications.RegisterPushTokenResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PushTokenManagerTest {
    private val session = mockk<SessionManager>(relaxed = true)
    private val client = mockk<NotificationServiceClient>()
    private val source = FcmTokenSource { "fcm-token-1" }
    private val manager = PushTokenManager(client, session, source)

    @Test
    fun `uploads the token as android and keeps the server device id`() =
        runTest {
            every { session.getAccessToken() } returns "jwt"
            every { session.getDeviceId() } returns "7"
            every { session.getFcmToken() } returns null
            coEvery { client.registerPushToken(any()) } returns RegisterPushTokenResponse(success = true)

            manager.uploadToken()

            coVerify { client.registerPushToken(RegisterPushTokenRequest(token = "fcm-token-1", platform = "android")) }
            verify { session.saveFcmToken("fcm-token-1") }
            verify(exactly = 0) { session.saveDeviceId(any()) }
        }

    @Test
    fun `skips the RPC when the token has not changed`() =
        runTest {
            every { session.getAccessToken() } returns "jwt"
            every { session.getFcmToken() } returns "fcm-token-1"

            manager.uploadToken()

            coVerify(exactly = 0) { client.registerPushToken(any()) }
        }

    @Test
    fun `defers when signed out`() =
        runTest {
            every { session.getAccessToken() } returns null

            manager.uploadToken()

            coVerify(exactly = 0) { client.registerPushToken(any()) }
        }
}
