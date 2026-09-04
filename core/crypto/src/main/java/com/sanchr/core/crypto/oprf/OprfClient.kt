package com.sanchr.core.crypto.oprf

import javax.inject.Inject
import javax.inject.Singleton

/** A blinded query: keep [scalar] on-device, send [blindedPoint] to the server. */
class OprfBlinding(
    val scalar: ByteArray,
    val blindedPoint: ByteArray,
)

class OprfException(
    message: String,
) : Exception(message)

/**
 * Typed wrapper over [OprfNative]: turns its null returns into
 * [OprfException] and enforces the 32-byte encodings the protocol uses, so
 * callers never hand the native layer a wrong-length array and never have to
 * reason about null.
 *
 * Protocol (2-HashDH OPRF over ristretto255, shared with iOS and the server):
 * `blindedPoint = r · H(phone)`; server returns `k · blindedPoint`;
 * [unblind] yields `k · H(phone)`, which is compared byte-for-byte against the
 * server's registered set.
 */
@Singleton
class OprfClient
    @Inject
    constructor() {
        init {
            OprfNativeLoader.load()
        }

        fun blind(phoneE164: String): OprfBlinding {
            val out = OprfNative.blind(phoneE164) ?: throw OprfException("blinding failed")
            if (out.size != BLIND_OUTPUT_SIZE) {
                throw OprfException("blind returned ${out.size} bytes, expected $BLIND_OUTPUT_SIZE")
            }
            return OprfBlinding(
                scalar = out.copyOfRange(0, POINT_SIZE),
                blindedPoint = out.copyOfRange(POINT_SIZE, BLIND_OUTPUT_SIZE),
            )
        }

        fun unblind(
            scalar: ByteArray,
            evaluated: ByteArray,
        ): ByteArray {
            if (scalar.size != POINT_SIZE || evaluated.size != POINT_SIZE) {
                throw OprfException(
                    "expected $POINT_SIZE-byte scalar and point, got ${scalar.size} and ${evaluated.size}",
                )
            }
            return OprfNative.unblind(scalar, evaluated)
                ?: throw OprfException("server response is not a valid ristretto255 point")
        }

        companion object {
            const val POINT_SIZE = 32
            private const val BLIND_OUTPUT_SIZE = POINT_SIZE * 2
        }
    }
