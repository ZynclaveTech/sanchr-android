package com.sanchr.core.network

import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * Provides configured gRPC [ManagedChannel] instances backed by OkHttp transport.
 *
 * Two channels are maintained:
 * - **Core** channel → [BuildConfig.GRPC_CORE_HOST] for Auth, Messaging, Keys,
 *   Contacts, Vault, Media, Settings, Notifications, Backup.
 * - **Call** channel → [BuildConfig.GRPC_CALL_HOST] for CallSignaling.
 *
 * A default 10 s unary deadline is applied channel-wide via
 * [grpc_service_config.json] loaded from classpath resources. The
 * [AuthInterceptor] and [UnauthenticatedRefreshInterceptor] are chained so
 * every RPC is authenticated outbound and observed inbound.
 */
@Singleton
class GrpcChannelProvider
    @Inject
    constructor(
        private val authInterceptor: AuthInterceptor,
        private val unauthenticatedRefreshInterceptor: UnauthenticatedRefreshInterceptor,
    ) {
        @Volatile
        private var coreChannel: ManagedChannel? = null

        @Volatile
        private var callChannel: ManagedChannel? = null

        private val serviceConfig: Map<String, Any?> by lazy { loadServiceConfig() }

        /** Returns the shared core [ManagedChannel] used by most services. */
        fun getCoreChannel(): ManagedChannel =
            coreChannel?.takeIf { !it.isShutdown && !it.isTerminated }
                ?: synchronized(this) {
                    coreChannel?.takeIf { !it.isShutdown && !it.isTerminated }
                        ?: buildChannel(BuildConfig.GRPC_CORE_HOST, BuildConfig.GRPC_CORE_PORT)
                            .also { coreChannel = it }
                }

        /** Returns the shared call [ManagedChannel] used by CallSignaling. */
        fun getCallChannel(): ManagedChannel =
            callChannel?.takeIf { !it.isShutdown && !it.isTerminated }
                ?: synchronized(this) {
                    callChannel?.takeIf { !it.isShutdown && !it.isTerminated }
                        ?: buildChannel(BuildConfig.GRPC_CALL_HOST, BuildConfig.GRPC_CALL_PORT)
                            .also { callChannel = it }
                }

        /** Backward-compatible alias; returns the core channel. */
        fun getChannel(): ManagedChannel = getCoreChannel()

        private fun buildChannel(
            host: String,
            port: Int,
        ): ManagedChannel =
            OkHttpChannelBuilder
                .forAddress(host, port)
                .useTransportSecurity()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(false)
                .idleTimeout(5, TimeUnit.MINUTES)
                .maxInboundMessageSize(16 * 1024 * 1024) // 16 MB
                .defaultServiceConfig(serviceConfig)
                .disableServiceConfigLookUp()
                .intercept(authInterceptor, unauthenticatedRefreshInterceptor)
                .build()

        /** Gracefully shuts down both channels. Call this during app cleanup. */
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

        private fun loadServiceConfig(): Map<String, Any?> {
            val url =
                javaClass.getResource("/grpc_service_config.json")
                    ?: error("grpc_service_config.json missing from core:network resources")
            return jsonObjectToMap(JSONObject(url.readText()))
        }

        private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                out[key] = unwrap(obj.opt(key))
            }
            return out
        }

        private fun jsonArrayToList(arr: JSONArray): List<Any?> {
            val out = ArrayList<Any?>(arr.length())
            for (i in 0 until arr.length()) {
                out.add(unwrap(arr.opt(i)))
            }
            return out
        }

        private fun unwrap(value: Any?): Any? =
            when (value) {
                null, JSONObject.NULL -> null
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                // gRPC's service-config parser requires Double for numeric values.
                is Int -> value.toDouble()
                is Long -> value.toDouble()
                is Float -> value.toDouble()
                else -> value
            }
    }
