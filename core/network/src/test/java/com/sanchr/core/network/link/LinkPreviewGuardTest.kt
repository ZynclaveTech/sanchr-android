package com.sanchr.core.network.link

import com.sanchr.core.network.link.LinkPreviewGuard.PublicOnlyDns
import com.sanchr.core.network.link.LinkPreviewGuard.PublicPeerInterceptor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Response

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
