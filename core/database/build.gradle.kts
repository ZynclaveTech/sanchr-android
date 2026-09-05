plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.sanchr.core.database"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // MigrationTest loads the exported schemas from the test APK's assets;
    // without this every migration test dies with "Cannot find the schema
    // file in the assets folder" before it can validate anything.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(projects.core.model)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.security.crypto)

    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.room.testing)
}
