package com.sanchr.core.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * BlurHash (woltapp/blurhash), the same algorithm iOS uses for
 * [MediaAttachment.blurHash]: a few DCT components packed into a short
 * base-83 string that decodes to a blurry placeholder before the real
 * image is downloaded and decrypted. Pure Kotlin over ARGB pixel arrays;
 * bitmap adapters live in the UI layer.
 */
object BlurHash {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"
    private const val MAX_COMPONENTS = 9
    private const val MIN_LENGTH = 6
    private const val AC_STEPS = 18
    private const val AC_BASE = 19
    private const val AC_BASE_SQ = AC_BASE * AC_BASE
    private const val MAX_QUANT = 82
    private const val QUANT_SCALE = 166f
    private const val BYTE = 255f
    private const val ALPHA_OPAQUE = 0xFF shl 24

    /**
     * Encodes [pixels] (row-major ARGB, [width] x [height]) with
     * [componentsX] x [componentsY] components (1..9 each). Alpha is ignored.
     */
    @Suppress("MagicNumber")
    fun encode(
        pixels: IntArray,
        width: Int,
        height: Int,
        componentsX: Int = 4,
        componentsY: Int = 3,
    ): String {
        require(componentsX in 1..MAX_COMPONENTS && componentsY in 1..MAX_COMPONENTS) { "components must be 1..9" }
        require(width > 0 && height > 0 && pixels.size >= width * height) { "pixel buffer does not match size" }
        val linear = FloatArray(width * height * 3)
        for (p in 0 until width * height) {
            val c = pixels[p]
            linear[p * 3] = srgbToLinear((c shr 16) and 0xFF)
            linear[p * 3 + 1] = srgbToLinear((c shr 8) and 0xFF)
            linear[p * 3 + 2] = srgbToLinear(c and 0xFF)
        }
        val factors = ArrayList<FloatArray>(componentsX * componentsY)
        for (j in 0 until componentsY) {
            for (i in 0 until componentsX) {
                factors += basisFactor(linear, width, height, i, j)
            }
        }
        val dc = factors[0]
        val ac = factors.drop(1)
        val sb = StringBuilder()
        sb.append(encode83((componentsX - 1) + (componentsY - 1) * MAX_COMPONENTS, 1))
        val maxValue: Float
        if (ac.isEmpty()) {
            maxValue = 1f
            sb.append(encode83(0, 1))
        } else {
            val actualMax = ac.maxOf { f -> maxOf(abs(f[0]), abs(f[1]), abs(f[2])) }
            val quantMax = floor(actualMax * QUANT_SCALE - 0.5f).toInt().coerceIn(0, MAX_QUANT)
            maxValue = (quantMax + 1) / QUANT_SCALE
            sb.append(encode83(quantMax, 1))
        }
        sb.append(encode83(encodeDc(dc), 4))
        ac.forEach { sb.append(encode83(encodeAc(it, maxValue), 2)) }
        return sb.toString()
    }

    /** Decodes [hash] into [width] x [height] opaque ARGB pixels, or null when it is not a valid BlurHash. */
    @Suppress("MagicNumber", "ReturnCount")
    fun decode(
        hash: String,
        width: Int,
        height: Int,
        punch: Float = 1f,
    ): IntArray? {
        if (hash.length < MIN_LENGTH || width <= 0 || height <= 0) return null
        val sizeFlag = decode83(hash, 0, 1) ?: return null
        val numY = sizeFlag / MAX_COMPONENTS + 1
        val numX = sizeFlag % MAX_COMPONENTS + 1
        if (hash.length != 4 + 2 * numX * numY) return null
        val quantMax = decode83(hash, 1, 2) ?: return null
        val maxValue = (quantMax + 1) / QUANT_SCALE * punch
        val colors = Array(numX * numY) { FloatArray(3) }
        colors[0] = decodeDc(decode83(hash, 2, 6) ?: return null)
        for (i in 1 until numX * numY) {
            colors[i] = decodeAc(decode83(hash, 4 + i * 2, 6 + i * 2) ?: return null, maxValue)
        }
        val out = IntArray(width * height)
        val cosX = Array(numX) { i -> FloatArray(width) { x -> cos(PI * x * i / width).toFloat() } }
        val cosY = Array(numY) { j -> FloatArray(height) { y -> cos(PI * y * j / height).toFloat() } }
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (j in 0 until numY) {
                    for (i in 0 until numX) {
                        val basis = cosX[i][x] * cosY[j][y]
                        val c = colors[j * numX + i]
                        r += c[0] * basis
                        g += c[1] * basis
                        b += c[2] * basis
                    }
                }
                out[y * width + x] = ALPHA_OPAQUE or (linearToSrgb(r) shl 16) or (linearToSrgb(g) shl 8) or linearToSrgb(b)
            }
        }
        return out
    }

    private fun basisFactor(
        linear: FloatArray,
        width: Int,
        height: Int,
        bx: Int,
        by: Int,
    ): FloatArray {
        val norm = if (bx == 0 && by == 0) 1f else 2f
        var r = 0f
        var g = 0f
        var b = 0f
        for (y in 0 until height) {
            val cy = cos(PI * by * y / height).toFloat()
            for (x in 0 until width) {
                val basis = norm * cos(PI * bx * x / width).toFloat() * cy
                val p = (y * width + x) * 3
                r += basis * linear[p]
                g += basis * linear[p + 1]
                b += basis * linear[p + 2]
            }
        }
        val scale = 1f / (width * height)
        return floatArrayOf(r * scale, g * scale, b * scale)
    }

    @Suppress("MagicNumber")
    private fun srgbToLinear(value: Int): Float {
        val v = value / BYTE
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    @Suppress("MagicNumber")
    private fun linearToSrgb(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        val s = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
        return (s * BYTE + 0.5f).toInt().coerceIn(0, 255)
    }

    private fun encodeDc(v: FloatArray): Int = (linearToSrgb(v[0]) shl 16) + (linearToSrgb(v[1]) shl 8) + linearToSrgb(v[2])

    @Suppress("MagicNumber")
    private fun decodeDc(v: Int): FloatArray =
        floatArrayOf(
            srgbToLinear(v shr 16),
            srgbToLinear((v shr 8) and 0xFF),
            srgbToLinear(
                v and 0xFF,
            ),
        )

    @Suppress("MagicNumber")
    private fun encodeAc(
        v: FloatArray,
        maxValue: Float,
    ): Int {
        fun q(x: Float): Int = (signPow(x / maxValue, 0.5f) * 9f + 9.5f).toInt().coerceIn(0, AC_STEPS)
        return q(v[0]) * AC_BASE_SQ + q(v[1]) * AC_BASE + q(v[2])
    }

    @Suppress("MagicNumber")
    private fun decodeAc(
        v: Int,
        maxValue: Float,
    ): FloatArray {
        fun c(q: Int): Float = signPow((q - 9f) / 9f, 2f) * maxValue
        return floatArrayOf(c(v / AC_BASE_SQ), c((v / AC_BASE) % AC_BASE), c(v % AC_BASE))
    }

    private fun signPow(
        value: Float,
        exp: Float,
    ): Float = if (exp == 0.5f) sign(value) * sqrt(abs(value)) else sign(value) * abs(value).pow(exp)

    private fun encode83(
        value: Int,
        length: Int,
    ): String {
        val sb = StringBuilder(length)
        for (i in 1..length) {
            var divisor = 1
            repeat(length - i) { divisor *= ALPHABET.length }
            sb.append(ALPHABET[(value / divisor) % ALPHABET.length])
        }
        return sb.toString()
    }

    private fun decode83(
        s: String,
        from: Int,
        to: Int,
    ): Int? {
        var value = 0
        for (i in from until to) {
            val idx = ALPHABET.indexOf(s[i])
            if (idx < 0) return null
            value = value * ALPHABET.length + idx
        }
        return value
    }
}
