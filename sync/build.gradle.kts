plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sanchr.sync"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Project modules
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.crypto)
    implementation(projects.core.notifications)
    implementation(projects.proto)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // WorkManager + Hilt integration
    implementation(libs.work.runtime)
    implementation(libs.work.hilt)

    // App Startup
    implementation(libs.startup.runtime)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // gRPC (for StatusException)
    implementation(libs.grpc.stub)
}
