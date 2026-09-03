package com.sanchr.core.network

/**
 * Methods that must reach the server with nothing identifying the caller.
 *
 * `SendSealedMessage` authenticates with a single-use, anonymous delivery
 * token. Attaching either a bearer token or `x-device-id` would tell the
 * server who sent an envelope whose whole purpose is that it cannot, so both
 * are withheld here rather than only the bearer token.
 */
object AnonymousMethods {
    val paths: Set<String> = setOf("sanchr.messaging.MessagingService/SendSealedMessage")

    fun isAnonymous(fullMethodName: String): Boolean = fullMethodName in paths
}
