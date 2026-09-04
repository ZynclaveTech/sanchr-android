package com.sanchr.core.crypto.oprf

import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.Assume.assumeTrue

/**
 * Runs the real JNI binding on the host JVM against the golden vectors.
 *
 * A plain unit test cannot load an Android `.so`, so this loads the same
 * crate built for the development machine (`cargo build -p sanchr-psi-jni
 * --release` in the backend), located via the `sanchr.psi.hostlib` system
 * property or the default sibling-checkout path. It is skipped — visibly, as
 * a JUnit assumption — when neither exists, so CI without a Rust toolchain
 * stays green while a developer with one gets a real proof of the marshalling
 * before anything is pushed. `OprfNativeSmokeTest` in the app module's
 * androidTest runs the same checks on the vendored `.so` on-device — it lives
 * there, not here, because CI only executes the app's `@SmokeTest` suite.
 */
class OprfNativeHostTest {
    private val vectors: List<OprfVector> by lazy {
        parseOprfVectors(javaClass.getResource("/oprf_vectors.json")!!.readText())
    }

    @BeforeTest
    fun loadHostLibrary() {
        val lib = hostLibrary()
        assumeTrue("host libsanchr_psi_jni not built; run cargo build -p sanchr-psi-jni --release", lib != null)
        OprfNativeLoader.loadFrom(lib!!.absolutePath)
    }

    @Test
    fun `unblind reproduces every golden vector through JNI`() {
        val client = OprfClient()
        for (v in vectors) {
            assertContentEquals(v.setElement, client.unblind(v.blindingScalar, v.evaluated), "drift for ${v.phone}")
        }
    }

    @Test
    fun `an invalid server point comes back as a typed failure, not a crash`() {
        val v = vectors.first()
        assertNull(OprfNative.unblind(v.blindingScalar, ByteArray(32) { 0xFF.toByte() }))
        assertFailsWith<OprfException> { OprfClient().unblind(v.blindingScalar, ByteArray(32) { 0xFF.toByte() }) }
    }

    @Test
    fun `wrong-length inputs are rejected before reaching native code`() {
        assertFailsWith<OprfException> { OprfClient().unblind(ByteArray(31), ByteArray(32)) }
        assertFailsWith<OprfException> { OprfClient().unblind(ByteArray(32), ByteArray(33)) }
    }

    @Test
    fun `blind returns a scalar and a point of the expected sizes`() {
        val b = OprfClient().blind("+14155551234")
        assertEquals(32, b.scalar.size)
        assertEquals(32, b.blindedPoint.size)
        // Two blindings of the same input must differ: the scalar is random.
        val b2 = OprfClient().blind("+14155551234")
        assertFalse(b.blindedPoint.contentEquals(b2.blindedPoint), "blinding must be randomised")
    }

    private fun hostLibrary(): File? {
        System.getProperty("sanchr.psi.hostlib")?.let { p -> File(p).takeIf { it.isFile }?.let { return it } }
        // Module dir is android/sanchr-android/core/crypto; the backend is a sibling of android/.
        val backendRelease = File("../../../../backend/target/release").canonicalFile
        return listOf("libsanchr_psi_jni.dylib", "libsanchr_psi_jni.so")
            .map { File(backendRelease, it) }
            .firstOrNull { it.isFile }
    }
}
