package com.sanchr.core.network.link

import java.io.IOException
import java.net.InetAddress
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
        dns(PublicOnlyDns(Dns.SYSTEM)).addNetworkInterceptor(PublicPeerInterceptor())

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
