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
 * Reads the current access token + device id directly from [SessionManager] on
 * every RPC. EncryptedSharedPreferences resolves to a parsed Map after first
 * load, so the read is an O(1) dictionary lookup — no caching layer needed,
 * and no risk of serving a stale token after a session change (sign-in,
 * sign-out, refresh).
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
             * RequestOtp added in Phase 4b — phone-only OTP issuance is by
             * definition pre-auth.
             */
            private val UNAUTHENTICATED_METHODS: Set<String> =
                setOf(
                    "sanchr.auth.AuthService/Register",
                    "sanchr.auth.AuthService/RequestOtp",
                    "sanchr.auth.AuthService/VerifyOTP",
                    "sanchr.auth.AuthService/Login",
                    "sanchr.auth.AuthService/RefreshToken",
                )
        }

        /**
         * No-op kept for source-compat with existing callers (e.g.
         * [UnauthenticatedRefreshInterceptor]). With caching removed there is
         * nothing to invalidate — the next RPC reads fresh from SessionManager.
         */
        fun invalidateToken() {
            // Intentionally empty.
        }

        private fun currentToken(): String? = sessionManager.getAccessToken()

        private fun currentDeviceId(): String? = sessionManager.getDeviceId()

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
