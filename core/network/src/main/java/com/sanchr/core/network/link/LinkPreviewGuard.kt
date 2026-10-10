package com.sanchr.core.network.link

import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.UnknownHostException
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Keeps a link preview from reaching the device or its network, checked where
 * the connection is made rather than where the link is read.
 *
 * [LinkDetector] can only judge the text of the first URL. It cannot know
 * that `innocent.example` resolves to 192.168.1.1, or that a public page
 * answers with a redirect to `http://127.0.0.1/`. Both are decided here, on
 * every hop:
 *
 *  - The client never uses a proxy, and [NoProxyBypassInterceptor] refuses the
 *    fetch outright when one is configured, so the two checks below always
 *    see the real destination and a user's proxy is never bypassed.
 *  - [PublicOnlyDns] refuses a name any of whose addresses is private, before
 *    anything is connected.
 *  - [PublicPeerInterceptor] looks at the address the socket actually reached
 *    and refuses to send the request if it is private. This is what catches a
 *    literal address in a redirect, which never passes through DNS. By then a
 *    TCP handshake has happened; no request has been written and nothing is
 *    read back.
 */
object LinkPreviewGuard {
    fun OkHttpClient.Builder.refusePrivateAddresses(): OkHttpClient.Builder =
        // Direct only: through a proxy the name is resolved by the proxy and
        // the socket's peer is the proxy, so neither check below would be
        // looking at the destination.
        proxy(Proxy.NO_PROXY)
            .addInterceptor(NoProxyBypassInterceptor { ProxySelector.getDefault() })
            .dns(PublicOnlyDns(Dns.SYSTEM))
            .addNetworkInterceptor(PublicPeerInterceptor())

    /**
     * Refuses the fetch when the device is configured to use a proxy.
     *
     * The client connects directly so that the checks are sound, and that
     * must not become a way around a proxy the user set up: someone who
     * routes traffic through one would have their own address shown to
     * whatever site a message linked to. No preview is the honest outcome.
     */
    internal class NoProxyBypassInterceptor(
        private val selector: () -> ProxySelector?,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val configured = runCatching { selector()?.select(request.url.toUri()) }.getOrNull().orEmpty()
            if (configured.any { it.type() != Proxy.Type.DIRECT }) {
                throw IOException("link preview refused: a proxy is configured")
            }
            return chain.proceed(request)
        }
    }

    internal class PublicOnlyDns(
        private val delegate: Dns,
    ) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = delegate.lookup(hostname)
            // All of them, not the first: the connection may be made to any,
            // and one private record among public ones is the rebinding trick.
            if (addresses.any(PrivateAddresses::isPrivate)) {
                throw UnknownHostException("$hostname resolves to a private address")
            }
            return addresses
        }
    }

    internal class PublicPeerInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val peer = chain.connection()?.socket()?.inetAddress
            // No connection to inspect means the request cannot be shown to be
            // going somewhere public, so it does not go.
            if (peer == null || PrivateAddresses.isPrivate(peer)) {
                throw IOException("link preview refused: peer is not a public address")
            }
            return chain.proceed(chain.request())
        }
    }
}
