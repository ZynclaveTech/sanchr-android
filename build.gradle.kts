plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.addAll(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            )
        }
    }
}

allprojects {
    apply(
        plugin =
            rootProject.libs.plugins.ktlint
                .get()
                .pluginId,
    )
    apply(
        plugin =
            rootProject.libs.plugins.detekt
                .get()
                .pluginId,
    )
    apply(
        plugin =
            rootProject.libs.plugins.kover
                .get()
                .pluginId,
    )

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.3.1")
        android.set(true)
        ignoreFailures.set(false)
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        }
        filter {
            exclude { element -> element.file.path.contains("build/") }
            exclude { element -> element.file.path.contains("/generated/") }
        }
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        autoCorrect = false
        parallel = true
    }
}

dependencies {
    kover(project(":app"))
    kover(project(":proto"))
    kover(project(":core:common"))
    kover(project(":core:model"))
    kover(project(":core:crypto"))
    kover(project(":core:network"))
    kover(project(":core:database"))
    kover(project(":core:datastore"))
    kover(project(":core:notifications"))
    kover(project(":core:designsystem"))
    kover(project(":core:callengine"))
    kover(project(":domain:messaging"))
    kover(project(":domain:contacts"))
    kover(project(":domain:vault"))
    kover(project(":domain:calls"))
    kover(project(":feature:auth"))
    kover(project(":feature:chats"))
    kover(project(":feature:contacts"))
    kover(project(":feature:calls"))
    kover(project(":feature:vault"))
    kover(project(":feature:profile"))
    kover(project(":feature:settings"))
    kover(project(":sync"))
}

// ─────────────────────────────────────────────────────────────────────────────
// Kover line-coverage verification — DEFERRED (see note)
//
// The M6 plan (Phase 5.3) calls for a 70% line-coverage floor on
// `:domain:messaging` and `:core:crypto`. Current baseline on 2026-04-24:
//
//   :domain:messaging  51.4% (148/288 lines)
//   :core:crypto       46.6% (447/959 lines)
//
// Enforcing 70% today turns CI red; we'd need to ratchet from current
// coverage and raise the floor incrementally. Kover 0.9.0's
// `KoverVerifyRule` API, however, has no per-rule `filters { }` method
// (verified via `javap` on `kover-gradle-plugin-0.9.0.jar` — only
// `bound`/`minBound`/`maxBound`/`disabled`/`groupBy` are exposed). The
// only 0.9-supported way to gate a specific module is to put the verify
// rule inside that module's own `build.gradle.kts` — which would push
// Phase 5 over the 5-file cap (CLAUDE.md #2).
//
// Decision: ship Phase 5 without threshold enforcement. The
// `koverXmlReport` task still runs in CI and the coverage artifact is
// uploaded (see `android-ci.yml`), so coverage is visible in every PR —
// we just don't block the merge on it. Adding per-module verify blocks
// is a post-M6 follow-up tracked in the plan self-review.
// ─────────────────────────────────────────────────────────────────────────────
