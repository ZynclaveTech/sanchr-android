package com.sanchr.core.network

import com.sanchr.core.datastore.SessionManager
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [UnauthenticatedRefreshInterceptor]. We drive the interceptor
 * directly and invoke `onClose` on the captured listener to simulate the server
 * closing the RPC with various [Status] codes.
 */
class UnauthenticatedRefreshInterceptorTest {
    private val sessionManager =
        mockk<SessionManager>(relaxed = true).also {
            every { it.getAccessToken() } returns "tok"
            every { it.getDeviceId() } returns "dev"
        }

    private object BytesMarshaller : MethodDescriptor.Marshaller<ByteArray> {
        override fun stream(value: ByteArray) = value.inputStream()

        override fun parse(stream: java.io.InputStream): ByteArray = stream.readBytes()
    }

    private val method: MethodDescriptor<ByteArray, ByteArray> =
        MethodDescriptor
            .newBuilder<ByteArray, ByteArray>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("sanchr.test.TestService/Ping")
            .setRequestMarshaller(BytesMarshaller)
            .setResponseMarshaller(BytesMarshaller)
            .build()

    /** Records the listener so the test can fire onClose on it. */
    private class ListenerCapturingChannel : Channel() {
        var listener: ClientCall.Listener<*>? = null

        override fun <ReqT, RespT> newCall(
            methodDescriptor: MethodDescriptor<ReqT, RespT>,
            callOptions: CallOptions,
        ): ClientCall<ReqT, RespT> =
            object : ClientCall<ReqT, RespT>() {
                override fun start(
                    responseListener: Listener<RespT>,
                    headers: Metadata,
                ) {
                    listener = responseListener
                }

                override fun request(numMessages: Int) = Unit

                override fun cancel(
                    message: String?,
                    cause: Throwable?,
                ) = Unit

                override fun halfClose() = Unit

                override fun sendMessage(message: ReqT) = Unit
            }

        override fun authority(): String = "test"
    }

    private fun fire(status: Status): Pair<AtomicInteger, AuthInterceptor> {
        val triggered = AtomicInteger(0)
        val auth = AuthInterceptor(sessionManager)
        // Prime the cache by actually starting an RPC — this pulls the token.
        auth
            .interceptCall(method, CallOptions.DEFAULT, ListenerCapturingChannel())
            .start(object : ClientCall.Listener<ByteArray>() {}, Metadata())
        val interceptor = UnauthenticatedRefreshInterceptor(auth) { triggered.incrementAndGet() }

        val channel = ListenerCapturingChannel()
        val call = interceptor.interceptCall(method, CallOptions.DEFAULT, channel)
        call.start(object : ClientCall.Listener<ByteArray>() {}, Metadata())

        @Suppress("UNCHECKED_CAST")
        val listener = channel.listener as ClientCall.Listener<ByteArray>
        listener.onClose(status, Metadata())
        return triggered to auth
    }

    @Test
    fun `UNAUTHENTICATED status triggers callback and invalidates cached token`() {
        var reads = 0
        every { sessionManager.getAccessToken() } answers {
            reads++
            "tok-$reads"
        }

        val (triggered, auth) = fire(Status.UNAUTHENTICATED)

        assertEquals(1, triggered.get())
        // Issue another RPC: because the cache was invalidated, AuthInterceptor
        // must consult SessionManager again.
        val beforeReads = reads
        auth
            .interceptCall(method, CallOptions.DEFAULT, ListenerCapturingChannel())
            .start(object : ClientCall.Listener<ByteArray>() {}, Metadata())
        assertEquals(beforeReads + 1, reads)
    }

    @Test
    fun `OK status does not trigger callback`() {
        val (triggered, _) = fire(Status.OK)
        assertEquals(0, triggered.get())
    }

    @Test
    fun `NOT_FOUND status does not trigger callback`() {
        val (triggered, _) = fire(Status.NOT_FOUND)
        assertEquals(0, triggered.get())
    }
}
