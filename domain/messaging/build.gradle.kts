plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sanchr.domain.messaging"
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.crypto)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(projects.proto)
    // SendReadReceiptUseCase builds a Messaging.ReceiptUpdate (the generated
    // proto type) directly rather than a Kotlin DTO — see its class doc.
    // The proto module's own protobuf-lite runtime dep is `implementation`,
    // so it does not leak here transitively; core/network needs the same
    // two lines for the same reason (see its build.gradle.kts).
    implementation(libs.grpc.protobuf.lite)
    implementation(libs.protobuf.kotlin.lite)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)

    // Required so LogoutUseCase can call RoomDatabase#close() on SanchrDatabase.
    implementation(libs.room.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
