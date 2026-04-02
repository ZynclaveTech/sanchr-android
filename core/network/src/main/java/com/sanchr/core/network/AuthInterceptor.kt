package com.sanchr.core.network

import com.sanchr.core.datastore.SessionManager
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * gRPC client interceptor that attaches JWT bearer tokens to outgoing requests.
 * Reads the current access token from [SessionManager].
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : ClientInterceptor {

    companion object {
        private val AUTH_METADATA_KEY: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)

        private val DEVICE_ID_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-device-id", Metadata.ASCII_STRING_MARSHALLER)
    }

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel,
    ): ClientCall<ReqT, RespT> {
        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(method, callOptions)
        ) {
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                // TODO: Replace runBlocking with a non-blocking token retrieval strategy.
                // Consider caching the token in-memory and refreshing asynchronously.
                val token = runBlocking { sessionManager.getAccessToken() }
                if (token != null) {
                    headers.put(AUTH_METADATA_KEY, "Bearer $token")
                }

                val deviceId = runBlocking { sessionManager.getDeviceId() }
                if (deviceId != null) {
                    headers.put(DEVICE_ID_KEY, deviceId)
                }

                super.start(responseListener, headers)
            }
        }
    }
}
