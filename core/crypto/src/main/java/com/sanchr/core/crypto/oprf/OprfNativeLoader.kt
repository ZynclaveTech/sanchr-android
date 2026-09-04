package com.sanchr.core.crypto.oprf

/**
 * Loads `libsanchr_psi_jni` exactly once.
 *
 * Production calls [load], which resolves the `.so` vendored under
 * `core/crypto/src/main/jniLibs/<abi>/`. Host JVM tests call [loadFrom] with a
 * `.dylib`/`.so` built for the development machine, after which [load] is a
 * no-op — so the same [OprfClient] code path runs in both.
 */
object OprfNativeLoader {
    private const val LIBRARY = "sanchr_psi_jni"

    @Volatile
    private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return
        System.loadLibrary(LIBRARY)
        loaded = true
    }

    /** Test seam: load an explicit library path instead of the packaged one. */
    @Synchronized
    fun loadFrom(absolutePath: String) {
        if (loaded) return
        System.load(absolutePath)
        loaded = true
    }
}
