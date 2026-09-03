package com.sanchr.core.network

import com.sanchr.core.datastore.SessionManager
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises [AuthInterceptor] against a hand-rolled [Channel] stub that captures
 * the [Metadata] passed to [ClientCall.start]. This is simpler and faster than
 * spinning up an in-process gRPC server and catches the same regressions —
 * the interceptor's contract is purely "mutate headers before delegating".
 */
class AuthInterceptorTest {
    private val sessionManager = mockk<SessionManager>(relaxed = true)

    private val authKey: Metadata.Key<String> =
        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    private val deviceIdKey: Metadata.Key<String> =
        Metadata.Key.of("x-device-id", Metadata.ASCII_STRING_MARSHALLER)

    private fun fakeMethod(fullName: String): MethodDescriptor<ByteArray, ByteArray> =
        MethodDescriptor
            .newBuilder<ByteArray, ByteArray>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(fullName)
            .setRequestMarshaller(BytesMarshaller)
            .setResponseMarshaller(BytesMarshaller)
            .build()

    private object BytesMarshaller : MethodDescriptor.Marshaller<ByteArray> {
        override fun stream(value: ByteArray) = value.inputStream()

        override fun parse(stream: java.io.InputStream): ByteArray = stream.readBytes()
    }

    /** A [Channel] that hands out a [CapturingCall] so we can inspect headers. */
    private class CapturingChannel : Channel() {
        val capturedHeaders = mutableListOf<Metadata>()

        override fun <ReqT, RespT> newCall(
            methodDescriptor: MethodDescriptor<ReqT, RespT>,
            callOptions: CallOptions,
        ): ClientCall<ReqT, RespT> = CapturingCall(capturedHeaders)

        override fun authority(): String = "test"
    }

    private class CapturingCall<ReqT, RespT>(
        private val captured: MutableList<Metadata>,
    ) : ClientCall<ReqT, RespT>() {
        override fun start(
            responseListener: Listener<RespT>,
            headers: Metadata,
        ) {
            captured.add(headers)
        }

        override fun request(numMessages: Int) = Unit

        override fun cancel(
            message: String?,
            cause: Throwable?,
        ) = Unit

        override fun halfClose() = Unit

        override fun sendMessage(message: ReqT) = Unit
    }

    private fun capture(
        interceptor: AuthInterceptor,
        methodName: String,
    ): Metadata {
        val channel = CapturingChannel()
        val method = fakeMethod(methodName)
        val call = interceptor.interceptCall(method, CallOptions.DEFAULT, channel)
        call.start(
            object : ClientCall.Listener<ByteArray>() {},
            Metadata(),
        )
        return channel.capturedHeaders.single()
    }

    @Test
    fun `attaches bearer token for authenticated methods`() {
        every { sessionManager.getAccessToken() } returns "tok-123"
        every { sessionManager.getDeviceId() } returns "device-abc"
        val interceptor = AuthInterceptor(sessionManager)

        val headers = capture(interceptor, "sanchr.messaging.MessagingService/SendMessage")

        assertEquals("Bearer tok-123", headers.get(authKey))
        assertEquals("device-abc", headers.get(deviceIdKey))
    }

    @Test
    fun `skips bearer token for UNAUTHENTICATED methods but still attaches device id`() {
        every { sessionManager.getAccessToken() } returns "should-not-appear"
        every { sessionManager.getDeviceId() } returns "device-abc"
        val interceptor = AuthInterceptor(sessionManager)

        val headers = capture(interceptor, "sanchr.auth.AuthService/Register")

        assertNull(headers.get(authKey))
        assertEquals("device-abc", headers.get(deviceIdKey))
    }

    @Test
    fun `omits bearer header when no token is available`() {
        every { sessionManager.getAccessToken() } returns null
        every { sessionManager.getDeviceId() } returns null
        val interceptor = AuthInterceptor(sessionManager)

        val headers = capture(interceptor, "sanchr.messaging.MessagingService/SendMessage")

        assertNull(headers.get(authKey))
        assertNull(headers.get(deviceIdKey))
    }

    @Test
    fun `caches token and does not re-read from SessionManager on every call`() {
        var reads = 0
        every { sessionManager.getAccessToken() } answers {
            reads++
            "tok-123"
        }
        every { sessionManager.getDeviceId() } returns "device-abc"
        val interceptor = AuthInterceptor(sessionManager)

        capture(interceptor, "sanchr.messaging.MessagingService/SendMessage")
        capture(interceptor, "sanchr.messaging.MessagingService/SendMessage")
        capture(interceptor, "sanchr.messaging.MessagingService/SendMessage")

        assertEquals(1, reads)
    }

    @Test
    fun `invalidateToken forces re-read on next call`() {
        var reads = 0
        every { sessionManager.getAccessToken() } answers {
            reads++
            "tok-$reads"
        }
        every { sessionManager.getDeviceId() } returns "device-abc"
        val interceptor = AuthInterceptor(sessionManager)

        val first = capture(interceptor, "sanchr.messaging.MessagingService/SendMessage")
        interceptor.invalidateToken()
        val second = capture(interceptor, "sanchr.messaging.MessagingService/SendMessage")

        assertEquals("Bearer tok-1", first.get(authKey))
        assertEquals("Bearer tok-2", second.get(authKey))
        assertEquals(2, reads)
    }
}
