package com.sanchr.core.network

import com.sanchr.core.datastore.SessionManager
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * gRPC client interceptor that attaches JWT bearer tokens to outgoing requests.
 *
 * Token and device id are cached in-memory and refreshed lazily from [SessionManager]
 * on the first call after [invalidateToken] is invoked (or on cold start). This
 * avoids a blocking disk read on every RPC while keeping the interceptor fully
 * non-suspending — a hard requirement of the gRPC interceptor API.
 *
 * Methods listed in [UNAUTHENTICATED_METHODS] are skipped — no bearer token is
 * injected, since those endpoints are invoked precisely to obtain one.
 */
@Singleton
class AuthInterceptor
    @Inject
    constructor(
        private val sessionManager: SessionManager,
    ) : ClientInterceptor {
        companion object {
            private val AUTH_METADATA_KEY: Metadata.Key<String> =
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)

            private val DEVICE_ID_KEY: Metadata.Key<String> =
                Metadata.Key.of("x-device-id", Metadata.ASCII_STRING_MARSHALLER)

            /**
             * Fully-qualified gRPC method names that must NOT carry an auth token.
             */
            private val UNAUTHENTICATED_METHODS: Set<String> =
                setOf(
                    "sanchr.auth.AuthService/Register",
                    "sanchr.auth.AuthService/VerifyOTP",
                    "sanchr.auth.AuthService/Login",
                    "sanchr.auth.AuthService/RefreshToken",
                )
        }

        @Volatile
        private var cachedToken: String? = null

        @Volatile
        private var cachedDeviceId: String? = null

        /**
         * Clears the cached access token. The next outbound RPC will re-read
         * from [SessionManager]. Called by [UnauthenticatedRefreshInterceptor]
         * when the server rejects a request with [io.grpc.Status.UNAUTHENTICATED].
         */
        fun invalidateToken() {
            cachedToken = null
        }

        private fun currentToken(): String? {
            val cached = cachedToken
            if (cached != null) return cached
            val loaded = sessionManager.getAccessToken()
            if (loaded != null) {
                cachedToken = loaded
            }
            return loaded
        }

        private fun currentDeviceId(): String? {
            val cached = cachedDeviceId
            if (cached != null) return cached
            val loaded = sessionManager.getDeviceId()
            if (loaded != null) {
                cachedDeviceId = loaded
            }
            return loaded
        }

        override fun <ReqT, RespT> interceptCall(
            method: MethodDescriptor<ReqT, RespT>,
            callOptions: CallOptions,
            next: Channel,
        ): ClientCall<ReqT, RespT> {
            val skipAuth = method.fullMethodName in UNAUTHENTICATED_METHODS

            return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions),
            ) {
                override fun start(
                    responseListener: Listener<RespT>,
                    headers: Metadata,
                ) {
                    if (!skipAuth) {
                        val token = currentToken()
                        if (token != null) {
                            headers.put(AUTH_METADATA_KEY, "Bearer $token")
                        }
                    }

                    val deviceId = currentDeviceId()
                    if (deviceId != null) {
                        headers.put(DEVICE_ID_KEY, deviceId)
                    }

                    super.start(responseListener, headers)
                }
            }
        }
    }
