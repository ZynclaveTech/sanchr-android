package com.sanchr.core.network

import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides configured gRPC [ManagedChannel] instances backed by OkHttp transport.
 *
 * Two channels are maintained:
 * - **Core** channel → `api-dev.sanchr.com:443` for Auth, Messaging, Keys, Contacts,
 *   Vault, Media, Settings, and Notifications services.
 * - **Call** channel → `call-dev.sanchr.com:443` for the CallSignaling service.
 */
@Singleton
class GrpcChannelProvider @Inject constructor(
    private val authInterceptor: AuthInterceptor,
) {
    @Volatile
    private var coreChannel: ManagedChannel? = null

    @Volatile
    private var callChannel: ManagedChannel? = null

    /**
     * Returns the shared core [ManagedChannel] used by most services.
     */
    fun getCoreChannel(): ManagedChannel {
        return coreChannel?.takeIf { !it.isShutdown && !it.isTerminated }
            ?: synchronized(this) {
                coreChannel?.takeIf { !it.isShutdown && !it.isTerminated }
                    ?: buildChannel(BuildConfig.GRPC_CORE_HOST, BuildConfig.GRPC_CORE_PORT)
                        .also { coreChannel = it }
            }
    }

    /**
     * Returns the shared call [ManagedChannel] used by CallSignaling.
     */
    fun getCallChannel(): ManagedChannel {
        return callChannel?.takeIf { !it.isShutdown && !it.isTerminated }
            ?: synchronized(this) {
                callChannel?.takeIf { !it.isShutdown && !it.isTerminated }
                    ?: buildChannel(BuildConfig.GRPC_CALL_HOST, BuildConfig.GRPC_CALL_PORT)
                        .also { callChannel = it }
            }
    }

    /**
     * Backward-compatible alias; returns the core channel.
     */
    fun getChannel(): ManagedChannel = getCoreChannel()

    private fun buildChannel(host: String, port: Int): ManagedChannel {
        return OkHttpChannelBuilder
            .forAddress(host, port)
            .useTransportSecurity()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(false)
            .idleTimeout(5, TimeUnit.MINUTES)
            .maxInboundMessageSize(16 * 1024 * 1024) // 16 MB
            .intercept(authInterceptor)
            .build()
    }

    /**
     * Gracefully shuts down both channels. Call this during app cleanup.
     */
    fun shutdown() {
        synchronized(this) {
            shutdownChannel(coreChannel)
            coreChannel = null
            shutdownChannel(callChannel)
            callChannel = null
        }
    }

    private fun shutdownChannel(channel: ManagedChannel?) {
        channel?.let { ch ->
            if (!ch.isShutdown) {
                ch.shutdown()
                try {
                    if (!ch.awaitTermination(5, TimeUnit.SECONDS)) {
                        ch.shutdownNow()
                    }
                } catch (e: InterruptedException) {
                    ch.shutdownNow()
                    Thread.currentThread().interrupt()
                }
            }
        }
    }
}
