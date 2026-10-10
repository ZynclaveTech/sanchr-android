package com.sanchr.core.network.link

import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.UnknownHostException
import okhttp3.Dns
import okhttp3.HttpUrl
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
 *  - The client never uses a proxy, and [CheckedRedirectsInterceptor] follows
 *    redirects itself, refusing any hop that names a private host or that
 *    the device would send through a proxy.
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
            // Redirects are followed by [CheckedRedirectsInterceptor], one
            // checked hop at a time, not by OkHttp behind its back.
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(CheckedRedirectsInterceptor { ProxySelector.getDefault() })
            .dns(PublicOnlyDns(Dns.SYSTEM))
            .addNetworkInterceptor(PublicPeerInterceptor())

    /**
     * Follows redirects itself so that every URL in the chain is judged
     * before anything connects to it, the first included.
     *
     * Two things are refused at each hop:
     *
     *  - A host that names the device or its network. The first URL has
     *    been through [LinkDetector] already; a `Location` header has not.
     *  - Any URL the device would send through a proxy. The client connects
     *    directly so that the address checks are sound, and that must not
     *    become a way around a proxy the user set up: their own address
     *    would be shown to whatever site a message linked to, or to wherever
     *    that site redirected. If the proxy configuration cannot be read the
     *    answer is the same — unknown is not "none".
     */
    internal class CheckedRedirectsInterceptor(
        private val selector: () -> ProxySelector?,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            repeat(MAX_HOPS) {
                check(request.url)
                val response = chain.proceed(request)
                if (!response.isRedirect) return response
                val next = response.header("Location")?.let(request.url::resolve)
                response.close()
                request = request.newBuilder().url(next ?: throw IOException("link preview refused: unusable redirect")).build()
            }
            throw IOException("link preview refused: too many redirects")
        }

        private fun check(url: HttpUrl) {
            if (LinkDetector.isPrivateHost(url.host.removeSuffix(".").lowercase())) {
                throw IOException("link preview refused: private host")
            }
            val direct =
                try {
                    selector()?.select(url.toUri()).orEmpty().all { it.type() == Proxy.Type.DIRECT }
                } catch (e: IllegalArgumentException) {
                    false
                } catch (e: SecurityException) {
                    false
                }
            if (!direct) throw IOException("link preview refused: a proxy is configured")
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

    private const val MAX_HOPS = 5
}
