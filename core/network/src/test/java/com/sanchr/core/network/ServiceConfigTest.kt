package com.sanchr.core.network

import io.grpc.MethodDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sanchr.calling.CallSignalingServiceGrpc
import sanchr.messaging.MessagingServiceGrpc

/**
 * A deadline on a call limits its whole life. The ten seconds meant for
 * unary calls was applied to every method, so the realtime message stream —
 * and a call's signalling stream — were closed ten seconds after opening.
 */
class ServiceConfigTest {
    private val methodConfigs: List<JsonObject> =
        Json
            .parseToJsonElement(checkNotNull(javaClass.getResource("/grpc_service_config.json")).readText())
            .jsonObject
            .getValue("methodConfig")
            .jsonArray
            .map { it.jsonObject }

    /** The entry that names [method] itself, as gRPC would pick it over the catch-all. */
    private fun configNaming(method: MethodDescriptor<*, *>): JsonObject? =
        methodConfigs.firstOrNull { config ->
            config.getValue("name").jsonArray.any { name ->
                val service = name.jsonObject["service"]?.jsonPrimitive?.content
                val bare = name.jsonObject["method"]?.jsonPrimitive?.content
                service != null && "$service/$bare" == method.fullMethodName
            }
        }

    private val allMethods =
        MessagingServiceGrpc.getServiceDescriptor().methods + CallSignalingServiceGrpc.getServiceDescriptor().methods

    @Test
    fun `a stream that stays open has no deadline`() {
        val longLived = allMethods.filter { it.type == MethodDescriptor.MethodType.BIDI_STREAMING }
        assertTrue(longLived.isNotEmpty())

        for (method in longLived) {
            val config = assertNotNull(configNaming(method), "${method.fullMethodName} falls to the ten-second catch-all")
            assertNull(config["timeout"], "${method.fullMethodName} would be closed when its timeout ran out")
        }
    }

    /** A sync ends by itself, but a long one takes more than ten seconds. */
    @Test
    fun `a stream that ends by itself gets longer than a unary call`() {
        val finite = allMethods.filter { it.type == MethodDescriptor.MethodType.SERVER_STREAMING }
        assertTrue(finite.isNotEmpty())

        for (method in finite) {
            val config = assertNotNull(configNaming(method), "${method.fullMethodName} falls to the ten-second catch-all")
            assertEquals("60s", config["timeout"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `everything else still has the ten-second deadline`() {
        val catchAll = methodConfigs.single { it.getValue("name").jsonArray.any { name -> name.jsonObject.isEmpty() } }

        assertEquals("10s", catchAll["timeout"]?.jsonPrimitive?.content)
        assertNull(configNaming(MessagingServiceGrpc.getSendSealedMessageMethod()))
    }
}
