import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// Per-machine overrides for gRPC backend host/port/TLS. Devs can set keys
// like `sanchr.grpc.host=10.0.2.2` in `local.properties` (gitignored) or
// inject `SANCHR_GRPC_HOST` etc. via env (CI). Defaults below preserve the
// production wiring (api-dev.sanchr.com:443, TLS on) so a fresh checkout
// without overrides behaves identically to before.
val sanchrLocalProperties =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

fun sanchrOverride(
    propKey: String,
    envKey: String,
    default: String,
): String =
    sanchrLocalProperties.getProperty(propKey)
        ?: System.getenv(envKey)
        ?: default

android {
    namespace = "com.sanchr.core.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "GRPC_CORE_HOST",
            "\"${sanchrOverride("sanchr.grpc.host", "SANCHR_GRPC_HOST", "api-dev.sanchr.com")}\"",
        )
        buildConfigField(
            "int",
            "GRPC_CORE_PORT",
            sanchrOverride("sanchr.grpc.port", "SANCHR_GRPC_PORT", "443"),
        )
        buildConfigField(
            "String",
            "GRPC_CALL_HOST",
            "\"${sanchrOverride("sanchr.grpc.call.host", "SANCHR_GRPC_CALL_HOST", "call-dev.sanchr.com")}\"",
        )
        buildConfigField(
            "int",
            "GRPC_CALL_PORT",
            sanchrOverride("sanchr.grpc.call.port", "SANCHR_GRPC_CALL_PORT", "443"),
        )
        buildConfigField(
            "boolean",
            "GRPC_USE_TLS",
            sanchrOverride("sanchr.grpc.tls", "SANCHR_GRPC_TLS", "true"),
        )
        buildConfigField(
            "String",
            "DEV_BACKEND_URL",
            "\"${project.findProperty("sanchr.devBackendUrl") ?: ""}\"",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.datastore)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf.lite)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.protobuf.kotlin.lite)
    implementation(libs.okhttp)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.grpc.testing)
    testImplementation(libs.grpc.inprocess)
    testImplementation(libs.grpc.stub)

    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.grpc.okhttp)
    androidTestImplementation(libs.grpc.stub)
    androidTestImplementation(projects.proto)
}
