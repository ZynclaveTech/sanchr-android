package com.sanchr.core.network.link

import com.sanchr.core.network.link.LinkPreviewGuard.CheckedRedirectsInterceptor
import com.sanchr.core.network.link.LinkPreviewGuard.PublicOnlyDns
import com.sanchr.core.network.link.LinkPreviewGuard.PublicPeerInterceptor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * The checks on where a preview actually connects — the part the text of a
 * link cannot answer: what a name resolves to, and where a redirect leads.
 */
class LinkPreviewGuardTest {
    private fun address(literal: String): InetAddress = InetAddress.getByName(literal)

    @Test
    fun `private IPv4 ranges are private and their neighbours are not`() {
        listOf("127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "0.0.0.0", "100.64.0.1", "224.0.0.1")
            .forEach { assertTrue(PrivateAddresses.isPrivate(address(it)), it) }
        listOf("93.184.216.34", "100.63.255.255", "100.128.0.1", "172.32.0.1", "8.8.8.8")
            .forEach { assertFalse(PrivateAddresses.isPrivate(address(it)), it) }
    }

    @Test
    fun `only global unicast IPv6 is public`() {
        listOf("::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "ff02::1", "64:ff9b::7f00:1", "::7f00:1")
            .forEach { assertTrue(PrivateAddresses.isPrivate(address(it)), it) }
        assertFalse(PrivateAddresses.isPrivate(address("2606:4700:4700::1111")))
    }

    @Test
    fun `IPv6 forms that carry an IPv4 address inside them are refused`() {
        // 6to4 and Teredo both sit inside 2000::/3 and both embed an IPv4
        // address: this one is 127.0.0.1.
        assertTrue(PrivateAddresses.isPrivate(address("2002:7f00:1::")))
        assertTrue(PrivateAddresses.isPrivate(address("2001:0:7f00:1::")))
        // An IPv4-mapped literal is read as the IPv4 address it names.
        assertTrue(PrivateAddresses.isPrivate(address("::ffff:127.0.0.1")))
    }

    @Test
    fun `a name that resolves to a private address is not resolved`() {
        val dns = PublicOnlyDns(resolvingTo("192.168.1.1"))
        assertFailsWith<UnknownHostException> { dns.lookup("innocent.example") }
    }

    @Test
    fun `one private record among public ones refuses the whole name`() {
        val dns = PublicOnlyDns(resolvingTo("93.184.216.34", "127.0.0.1"))
        assertFailsWith<UnknownHostException> { dns.lookup("rebind.example") }
    }

    @Test
    fun `a name that resolves publicly is passed through unchanged`() {
        val resolved = PublicOnlyDns(resolvingTo("93.184.216.34")).lookup("example.com")
        assertEquals(listOf(address("93.184.216.34")), resolved)
    }

    @Test
    fun `a request is not sent to a private peer, whatever URL led there`() {
        val chain = chainConnectedTo(address("127.0.0.1"))
        assertFailsWith<IOException> { PublicPeerInterceptor().intercept(chain) }
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `a request with no connection to inspect is not sent`() {
        val chain = mockk<Interceptor.Chain>(relaxed = true)
        every { chain.connection() } returns null
        assertFailsWith<IOException> { PublicPeerInterceptor().intercept(chain) }
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `a request to a public peer proceeds`() {
        val chain = chainConnectedTo(address("93.184.216.34"))
        val response = mockk<Response>()
        every { chain.proceed(any()) } returns response
        assertEquals(response, PublicPeerInterceptor().intercept(chain))
    }

    @Test
    fun `a preview is refused rather than fetched around a configured proxy`() {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.corp.example", 3128))
        val chain = chainAnswering()
        assertFailsWith<IOException> { CheckedRedirectsInterceptor { selecting { listOf(proxy) } }.intercept(chain) }
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `a proxy configuration that cannot be read counts as a proxy`() {
        val chain = chainAnswering()
        val broken = selecting { throw IllegalArgumentException("bad PAC") }
        assertFailsWith<IOException> { CheckedRedirectsInterceptor { broken }.intercept(chain) }
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `with no proxy configured the fetch goes ahead`() {
        val direct = CheckedRedirectsInterceptor { selecting { listOf(Proxy.NO_PROXY) } }
        assertEquals(200, direct.intercept(chainAnswering("https://example.com/a" to null)).code)
        assertEquals(200, CheckedRedirectsInterceptor { null }.intercept(chainAnswering("https://example.com/a" to null)).code)
    }

    @Test
    fun `a redirect to a public page is followed`() {
        val chain = chainAnswering("https://example.com/a" to "/b", "https://example.com/b" to null)
        val response = CheckedRedirectsInterceptor { null }.intercept(chain)
        assertEquals("https://example.com/b", response.request.url.toString())
    }

    @Test
    fun `a redirect to a private address is refused before anything connects to it`() {
        val privateTargets =
            listOf("http://127.0.0.1/admin", "http://[::1]/", "http://192.168.1.1/", "http://router.local/", "http://2130706433/")
        privateTargets.forEach { target ->
            val chain = chainAnswering("https://example.com/a" to target)
            assertFailsWith<IOException>(target) { CheckedRedirectsInterceptor { null }.intercept(chain) }
            // Once, for the public page that answered with the redirect.
            verify(exactly = 1) { chain.proceed(any()) }
        }
    }

    @Test
    fun `a redirect that only a proxy would carry is refused`() {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.corp.example", 3128))
        val onlyForOther = selecting { uri -> if (uri?.host == "other.example") listOf(proxy) else listOf(Proxy.NO_PROXY) }
        val chain = chainAnswering("https://example.com/a" to "https://other.example/x")
        assertFailsWith<IOException> { CheckedRedirectsInterceptor { onlyForOther }.intercept(chain) }
        verify(exactly = 1) { chain.proceed(any()) }
    }

    @Test
    fun `a redirect loop ends`() {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns Request.Builder().url("https://example.com/a").build()
        every { chain.proceed(any()) } answers { response(firstArg(), location = "/a") }
        assertFailsWith<IOException> { CheckedRedirectsInterceptor { null }.intercept(chain) }
        verify(exactly = 5) { chain.proceed(any()) }
    }

    @Test
    fun `the guarded client connects directly`() {
        val client = with(LinkPreviewGuard) { OkHttpClient.Builder().refusePrivateAddresses() }.build()
        assertEquals(Proxy.NO_PROXY, client.proxy)
        // Redirects belong to the checked interceptor, not to OkHttp.
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    /** A chain starting at the first URL, answering each URL with a redirect to its pair or, for null, a 200. */
    private fun chainAnswering(vararg hops: Pair<String, String?> = arrayOf("https://example.com/a" to null)): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        val answers = hops.toMap()
        every { chain.request() } returns Request.Builder().url(hops.first().first).build()
        every { chain.proceed(any()) } answers {
            val request = firstArg<Request>()
            response(request, location = answers[request.url.toString()])
        }
        return chain
    }

    private fun response(
        request: Request,
        location: String?,
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(if (location == null) 200 else 302)
            .message("")
            .apply { location?.let { header("Location", it) } }
            .body("".toResponseBody())
            .build()

    private fun selecting(choose: (URI?) -> List<Proxy>): ProxySelector =
        object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = choose(uri)

            override fun connectFailed(
                uri: URI?,
                sa: SocketAddress?,
                ioe: IOException?,
            ) = Unit
        }

    private fun resolvingTo(vararg literals: String): Dns =
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = literals.map(::address)
        }

    private fun chainConnectedTo(peer: InetAddress): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>(relaxed = true)
        every { chain.connection()?.socket()?.inetAddress } returns peer
        return chain
    }
}
