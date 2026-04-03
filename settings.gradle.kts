pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://build-artifacts.signal.org/libraries/maven/") }
    }
}

rootProject.name = "sanchr-android"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

// Proto / gRPC stubs
include(":proto")

// Core modules
include(":core:common")
include(":core:designsystem")
include(":core:model")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:crypto")
include(":core:notifications")
include(":core:callengine")

// Domain modules
include(":domain:messaging")
include(":domain:contacts")
include(":domain:vault")
include(":domain:calls")

// Feature modules
include(":feature:auth")
include(":feature:chats")
include(":feature:calls")
include(":feature:contacts")
include(":feature:vault")
include(":feature:settings")
include(":feature:profile")

// Background sync
include(":sync")
