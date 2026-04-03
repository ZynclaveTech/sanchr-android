plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sanchr.core.crypto"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(projects.core.common)
    implementation(projects.core.datastore)
    implementation(projects.proto)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.libsignal.android)
    // Exposed as api because public classes (SignalKeyManager, SignalSessionManager)
    // return/accept libsignal types (IdentityKeyPair, PreKeyBundle, etc.)
    api(libs.libsignal.client)
    implementation(libs.tink.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.security.crypto)
}
