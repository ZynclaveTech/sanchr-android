package com.sanchr.core.network

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides a configured gRPC [ManagedChannel] backed by OkHttp transport.
 * Singleton to avoid creating multiple channels to the same backend.
 */
@Singleton
class GrpcChannelProvider @Inject constructor(
    private val authInterceptor: AuthInterceptor,
) {
    @Volatile
    private var channel: ManagedChannel? = null

    /**
     * Returns a shared [ManagedChannel] instance. Creates one if it doesn't exist
     * or if the previous one was shut down.
     */
    fun getChannel(): ManagedChannel {
        return channel?.takeIf { !it.isShutdown && !it.isTerminated }
            ?: synchronized(this) {
                channel?.takeIf { !it.isShutdown && !it.isTerminated }
                    ?: createChannel().also { channel = it }
            }
    }

    private fun createChannel(): ManagedChannel {
        return OkHttpChannelBuilder
            .forAddress(BuildConfig.GRPC_HOST, BuildConfig.GRPC_PORT)
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
     * Gracefully shuts down the channel. Call this during app cleanup.
     */
    fun shutdown() {
        synchronized(this) {
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
            channel = null
        }
    }
}
