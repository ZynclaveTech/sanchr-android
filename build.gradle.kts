plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
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
