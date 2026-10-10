package com.sanchr.core.network.link

import java.net.Inet6Address
import java.net.InetAddress

/**
 * Which addresses a message is not allowed to make this device connect to.
 *
 * One predicate, used both where a link is read ([LinkDetector]) and where a
 * connection is made ([LinkPreviewGuard]), so the two cannot drift apart.
 */
object PrivateAddresses {
    fun isPrivate(address: InetAddress): Boolean {
        if (isReservedByJava(address)) return true
        val bytes = address.address
        return if (address is Inet6Address) !isPublicIpv6(bytes) else isCarrierGradeNat(bytes)
    }

    /** The ranges [InetAddress] already names: loopback, 0.0.0.0, link-local, site-local, multicast. */
    private fun isReservedByJava(address: InetAddress): Boolean =
        address.isLoopbackAddress ||
            address.isAnyLocalAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress

    /** 100.64.0.0/10: another machine on the same provider's network, not a site on the internet. */
    private fun isCarrierGradeNat(bytes: ByteArray): Boolean =
        bytes.size == IPV4_BYTES && octet(bytes, 0) == CGNAT_FIRST_OCTET && octet(bytes, 1) in CGNAT_SECOND_OCTETS

    /**
     * IPv6 is allow-listed rather than deny-listed: only global unicast
     * (2000::/3) is public, which leaves out unique-local (fc00::/7), the
     * IPv4-compatible and NAT64 forms and everything not yet assigned in one
     * rule. Within it, 6to4 (2002::/16) and Teredo (2001:0::/32) are refused
     * too — both carry an IPv4 address inside them, so `2002:7f00:1::` is a
     * way of writing 127.0.0.1 that no IPv4 check would see.
     */
    private fun isPublicIpv6(bytes: ByteArray): Boolean {
        if (bytes.size != IPV6_BYTES || octet(bytes, 0) and GLOBAL_UNICAST_MASK != GLOBAL_UNICAST_PREFIX) return false
        val firstGroup = (octet(bytes, 0) shl BITS_PER_BYTE) or octet(bytes, 1)
        val secondGroup = (octet(bytes, 2) shl BITS_PER_BYTE) or octet(bytes, 3)
        val sixToFour = firstGroup == SIX_TO_FOUR_GROUP
        val teredo = firstGroup == TEREDO_FIRST_GROUP && secondGroup == 0
        return !sixToFour && !teredo
    }

    private fun octet(
        bytes: ByteArray,
        index: Int,
    ): Int = bytes[index].toInt() and BYTE_MASK

    private const val IPV4_BYTES = 4
    private const val IPV6_BYTES = 16
    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8
    private const val CGNAT_FIRST_OCTET = 100
    private val CGNAT_SECOND_OCTETS = 64..127
    private const val GLOBAL_UNICAST_MASK = 0xE0
    private const val GLOBAL_UNICAST_PREFIX = 0x20
    private const val SIX_TO_FOUR_GROUP = 0x2002
    private const val TEREDO_FIRST_GROUP = 0x2001
}
