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
        // Base64-encoded ECPublicKey (33 bytes) of the sealed-sender TrustRoot.
        // Empty default — SealedSenderCipher throws if decrypt is invoked without
        // a wired TrustRoot. Populated in M3 once the backend spec is confirmed.
        buildConfigField("String", "SEALED_SENDER_TRUST_ROOT", "\"\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(projects.core.common)
    implementation(projects.core.datastore)
    implementation(projects.core.database)
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

    // Unit testing with in-memory Room via Robolectric
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.runtime)
    testImplementation(libs.room.ktx)
    testImplementation(libs.mockk)
}
