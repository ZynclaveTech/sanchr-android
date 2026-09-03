import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// ─────────────────────────────────────────────────────────────────────────────
// App version
//
// Source of truth: `app/version.properties`. The file is checked in and
// human-editable; every release (including hotfixes) bumps `versionCode`
// monotonically and updates `versionName` per SemVer.
//
// CI can override without touching the file by setting `SANCHR_VERSION_CODE`
// / `SANCHR_VERSION_NAME` — useful for deterministic builds off a tag.
//
// See `CHANGELOG.md` for the human-readable history and
// `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` for the
// Play-console internal-track release notes that ship with each upload.
// ─────────────────────────────────────────────────────────────────────────────
val appVersionProps =
    Properties().apply {
        val f = rootProject.file("app/version.properties")
        if (f.exists()) {
            f.inputStream().use { load(it) }
        }
    }

val appVersionCode: Int =
    (System.getenv("SANCHR_VERSION_CODE") ?: appVersionProps.getProperty("versionCode", "1")).toInt()

val appVersionName: String =
    System.getenv("SANCHR_VERSION_NAME") ?: appVersionProps.getProperty("versionName", "1.0.0")

android {
    namespace = "com.sanchr.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sanchr.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing is driven entirely by environment variables so no
    // key material or passphrase is ever committed. All four vars must be
    // set; if any are missing we leave signingConfig unset and a guard task
    // (see bottom of file) fails `assembleRelease` with a clear message
    // rather than silently producing an unsigned APK. See
    // `docs/android/release-signing.md`.
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("SANCHR_RELEASE_STORE_FILE")
            val storePasswordEnv = System.getenv("SANCHR_RELEASE_STORE_PASSWORD")
            val keyAliasEnv = System.getenv("SANCHR_RELEASE_KEY_ALIAS")
            val keyPasswordEnv = System.getenv("SANCHR_RELEASE_KEY_PASSWORD")
            if (!storeFilePath.isNullOrBlank() &&
                !storePasswordEnv.isNullOrBlank() &&
                !keyAliasEnv.isNullOrBlank() &&
                !keyPasswordEnv.isNullOrBlank()
            ) {
                storeFile = file(storeFilePath)
                storePassword = storePasswordEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
                // v1 + v2 + v3 scheme so the APK is accepted by Play and
                // verifiable on pre-Pie devices.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Only attach the release signing config when env vars populated
            // it (storeFile is non-null). Otherwise the guard task below
            // aborts the build before an unsigned APK can be produced.
            signingConfigs.findByName("release")?.takeIf { it.storeFile != null }?.let {
                signingConfig = it
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Core modules
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.crypto)
    implementation(projects.core.notifications)
    implementation(projects.core.callengine)

    // Domain modules
    implementation(projects.domain.messaging)
    implementation(projects.domain.contacts)
    implementation(projects.domain.vault)
    implementation(projects.domain.calls)

    // Feature modules
    implementation(projects.feature.auth)
    implementation(projects.feature.chats)
    implementation(projects.feature.calls)
    implementation(projects.feature.contacts)
    implementation(projects.feature.vault)
    implementation(projects.feature.settings)
    implementation(projects.feature.profile)

    // Proto (gRPC service clients)
    implementation(projects.proto)

    // Sync
    implementation(projects.sync)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // gRPC (needed by DI module to construct service clients)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.okhttp)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // WorkManager
    implementation(libs.work.runtime)
    implementation(libs.work.hilt)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// ─────────────────────────────────────────────────────────────────────────────
// Release-signing guard
//
// If `assembleRelease`, `bundleRelease`, or `packageRelease` is invoked
// without the four `SANCHR_RELEASE_*` environment variables populated, fail
// early with a clear message pointing at the docs. Without this guard AGP
// would silently produce an unsigned APK which is unshippable AND easy to
// miss in local workflows.
//
// Configuration-cache safe: the `doFirst` below reads the four env vars
// directly via `System.getenv` at execution time — capturing no script
// references or Project-level state.
// ─────────────────────────────────────────────────────────────────────────────
val releaseSigningTasks = setOf("assembleRelease", "bundleRelease", "packageRelease")

tasks
    .matching { it.name in releaseSigningTasks }
    .configureEach {
        doFirst {
            val required =
                listOf(
                    "SANCHR_RELEASE_STORE_FILE",
                    "SANCHR_RELEASE_STORE_PASSWORD",
                    "SANCHR_RELEASE_KEY_ALIAS",
                    "SANCHR_RELEASE_KEY_PASSWORD",
                )
            val missing = required.filter { System.getenv(it).isNullOrBlank() }
            if (missing.isNotEmpty()) {
                throw GradleException(
                    """
                    Release signing config is not populated.
                    Missing environment variables: ${missing.joinToString()}.
                    Set all four before running a release build:
                      • SANCHR_RELEASE_STORE_FILE     (absolute path to the .jks keystore)
                      • SANCHR_RELEASE_STORE_PASSWORD
                      • SANCHR_RELEASE_KEY_ALIAS
                      • SANCHR_RELEASE_KEY_PASSWORD
                    See docs/android/release-signing.md for generation + rotation steps.
                    """.trimIndent(),
                )
            }
        }
    }
