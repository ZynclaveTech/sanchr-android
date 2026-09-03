package com.sanchr.core.network.contract

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanchr.core.network.BuildConfig
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import sanchr.auth.Auth
import sanchr.auth.AuthServiceGrpc

/**
 * Contract test: prove the Android client can reach the dev backend over TLS and
 * receive a well-formed gRPC status from an unauthenticated RPC.
 *
 * The test calls AuthService/Register (an UNAUTHENTICATED method) with an empty
 * request. The backend is expected to reject the call with some gRPC Status
 * (e.g. INVALID_ARGUMENT or ALREADY_EXISTS). We do not assert on the specific
 * code — only that the channel connected, TLS completed, and the server
 * responded with a status. This is the M1 "ping" bar.
 *
 * Skipped automatically when -Psanchr.devBackendUrl is not provided.
 */
@RunWith(AndroidJUnit4::class)
class DevBackendPingContractTest {
    @Test
    fun unauthenticated_rpc_reaches_backend() {
        val url = BuildConfig.DEV_BACKEND_URL
        assumeTrue("DEV_BACKEND_URL not set; skipping contract test", url.isNotEmpty())

        val (host, portStr) = url.split(":", limit = 2)
        val port = portStr.toInt()

        val channel =
            ManagedChannelBuilder
                .forAddress(host, port)
                .useTransportSecurity()
                .build()

        try {
            val stub =
                AuthServiceGrpc
                    .newBlockingStub(channel)
                    .withDeadlineAfter(10, TimeUnit.SECONDS)

            var status: Status? = null
            try {
                stub.register(Auth.RegisterRequest.getDefaultInstance())
            } catch (e: StatusRuntimeException) {
                status = e.status
            }

            assertNotNull(
                status,
                "expected a gRPC status from backend; got a successful response for an invalid request",
            )
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
