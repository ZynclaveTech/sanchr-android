package com.sanchr.core.crypto.oprf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the **vendored** `libsanchr_psi_jni.so` on-device against the golden
 * vectors. This is the check that catches a stale or wrong binary, an ABI
 * mismatch, or a JNI marshalling slip — each of which would otherwise surface
 * only as "contact discovery finds nobody."
 *
 * The vectors are the same file the backend pins in `cargo test`; if the
 * protocol or the binary changes, both must be regenerated together.
 */
@RunWith(AndroidJUnit4::class)
class OprfNativeTest {
    private val vectors: List<OprfVector> by lazy {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        parseOprfVectors(assets.open("oprf_vectors.json").bufferedReader().readText())
    }

    @Test
    fun unblindReproducesEveryGoldenVector() {
        val client = OprfClient() // loads the packaged .so
        for (v in vectors) {
            assertContentEquals(v.setElement, client.unblind(v.blindingScalar, v.evaluated), "drift for ${v.phone}")
        }
    }

    @Test
    fun invalidServerPointIsATypedFailure() {
        val v = vectors.first()
        OprfClient()
        assertNull(OprfNative.unblind(v.blindingScalar, ByteArray(32) { 0xFF.toByte() }))
        assertFailsWith<OprfException> { OprfClient().unblind(v.blindingScalar, ByteArray(32) { 0xFF.toByte() }) }
    }

    @Test
    fun blindProducesDistinctBlindingsOfTheSameInput() {
        val client = OprfClient()
        val a = client.blind("+14155551234")
        val b = client.blind("+14155551234")
        assertEquals(32, a.scalar.size)
        assertEquals(32, a.blindedPoint.size)
        assertFalse(a.blindedPoint.contentEquals(b.blindedPoint), "blinding must be randomised")
    }
}
