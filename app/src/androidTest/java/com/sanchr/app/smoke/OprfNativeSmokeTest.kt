package com.sanchr.app.smoke

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanchr.core.crypto.oprf.OprfClient
import com.sanchr.core.crypto.oprf.OprfException
import com.sanchr.core.crypto.oprf.OprfNative
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the **vendored** `libsanchr_psi_jni.so` on-device against the OPRF
 * golden vectors.
 *
 * This lives in the app module, tagged [SmokeTest], because that is the only
 * instrumented suite CI executes (`:app:connectedDebugAndroidTest` filtered to
 * this annotation). A copy in `core:crypto`'s androidTest would compile and
 * never run. It is the check that catches a stale or wrong binary, an ABI
 * mismatch, or a JNI marshalling slip — each of which would otherwise surface
 * only as "contact discovery finds nobody."
 *
 * The host-side counterpart, `OprfNativeHostTest` in `core:crypto`, runs the
 * same vectors on a developer's machine before push.
 */
@SmokeTest
@RunWith(AndroidJUnit4::class)
class OprfNativeSmokeTest {
    private val vectors: List<OprfVector> by lazy {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        parseOprfVectors(assets.open("oprf_vectors.json").bufferedReader().readText())
    }

    @Test
    fun unblindReproducesEveryGoldenVector() {
        val client = OprfClient() // loads the packaged .so
        assertFalse("fixture has no vectors", vectors.isEmpty())
        for (v in vectors) {
            assertArrayEquals("drift for ${v.phone}", v.setElement, client.unblind(v.blindingScalar, v.evaluated))
        }
    }

    @Test
    fun invalidServerPointIsATypedFailureNotACrash() {
        val client = OprfClient()
        val v = vectors.first()
        val garbage = ByteArray(32) { 0xFF.toByte() }
        assertNull(OprfNative.unblind(v.blindingScalar, garbage))
        assertThrows(OprfException::class.java) { client.unblind(v.blindingScalar, garbage) }
    }

    @Test
    fun blindProducesDistinctBlindingsOfTheSameInput() {
        val client = OprfClient()
        val a = client.blind("+14155551234")
        val b = client.blind("+14155551234")
        assertEquals(32, a.scalar.size)
        assertEquals(32, a.blindedPoint.size)
        assertFalse("blinding must be randomised", a.blindedPoint.contentEquals(b.blindedPoint))
    }
}
