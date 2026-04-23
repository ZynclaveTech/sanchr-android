package com.sanchr.core.network

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.ForwardingClientCallListener
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes response status on every gRPC call. When the server closes a call with
 * [Status.Code.UNAUTHENTICATED], it:
 *  1. Invalidates the cached bearer token in [AuthInterceptor] so the next RPC
 *     re-reads from persistent storage (where a refresh flow may have written a
 *     new token).
 *  2. Fires the [OnUnauthenticated] callback, allowing higher layers to kick off
 *     a token refresh or navigate to sign-in.
 *
 * The default [OnUnauthenticated] wired in [com.sanchr.core.network.di.NetworkModule]
 * is a no-op; the real refresh flow is wired in M4.
 */
@Singleton
class UnauthenticatedRefreshInterceptor
    @Inject
    constructor(
        private val authInterceptor: AuthInterceptor,
        private val onUnauthenticated: OnUnauthenticated,
    ) : ClientInterceptor {
        fun interface OnUnauthenticated {
            fun trigger()
        }

        override fun <ReqT, RespT> interceptCall(
            method: MethodDescriptor<ReqT, RespT>,
            callOptions: CallOptions,
            next: Channel,
        ): ClientCall<ReqT, RespT> =
            object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions),
            ) {
                override fun start(
                    responseListener: Listener<RespT>,
                    headers: Metadata,
                ) {
                    super.start(
                        object : ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
                            responseListener,
                        ) {
                            override fun onClose(
                                status: Status,
                                trailers: Metadata,
                            ) {
                                if (status.code == Status.Code.UNAUTHENTICATED) {
                                    authInterceptor.invalidateToken()
                                    onUnauthenticated.trigger()
                                }
                                super.onClose(status, trailers)
                            }
                        },
                        headers,
                    )
                }
            }
    }
