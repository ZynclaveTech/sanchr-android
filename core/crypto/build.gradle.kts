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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Base64-encoded ECPublicKey (33 bytes) of the sealed-sender TrustRoot.
        // Supplied at build time via the `sanchr.sealedSenderTrustRoot` Gradle
        // property — empty default so [BuildConfigTrustRootProvider] throws a
        // loud error if sealed-sender decrypt is attempted in an unconfigured
        // build. Production / CI builds set the property; see [SealedSenderCipher].
        buildConfigField(
            "String",
            "SEALED_SENDER_TRUST_ROOT",
            "\"${project.findProperty("sanchr.sealedSenderTrustRoot") ?: ""}\"",
        )
        // iOS → Android interop contract test fixtures. All four are empty by
        // default so [SealedSenderInteropContractTest] skips via assumeTrue in
        // unconfigured builds. CI builds that have captured a fixture from the
        // iOS test harness set these Gradle properties to wire the fixture in.
        buildConfigField(
            "String",
            "IOS_TEST_FIXTURE_ENVELOPE",
            "\"${project.findProperty("sanchr.iosTestFixtureEnvelope") ?: ""}\"",
        )
        buildConfigField(
            "String",
            "IOS_TEST_FIXTURE_EXPECTED_PLAINTEXT",
            "\"${project.findProperty("sanchr.iosTestFixtureExpectedPlaintext") ?: ""}\"",
        )
        buildConfigField(
            "Long",
            "IOS_TEST_FIXTURE_TIMESTAMP",
            "${project.findProperty("sanchr.iosTestFixtureTimestamp") ?: "0"}L",
        )
        buildConfigField(
            "String",
            "IOS_TEST_FIXTURE_SINK_PATH",
            "\"${project.findProperty("sanchr.iosTestFixtureSinkPath") ?: ""}\"",
        )
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

    packaging {
        resources {
            // MockK's android variant pulls in JUnit Jupiter which carries duplicate
            // META-INF license/notice files; merging those into the androidTest APK
            // is unnecessary for test execution.
            excludes +=
                setOf(
                    "META-INF/LICENSE.md",
                    "META-INF/LICENSE-notice.md",
                    "META-INF/NOTICE.md",
                )
        }
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

    // androidTest — real SQLCipher-backed DB round-trip tests on device/emulator.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockk)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.sqlcipher.android)
}
