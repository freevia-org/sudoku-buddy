pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "sudoku-buddy"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":core:model")
include(":core:solver")
include(":shared")
include(":core:presentation")
include(":core:image")
include(":core:recognize")
// iOS development needs neither the Android SDK nor the Java OpenCV pipeline.
if (providers.gradleProperty("iosOnly").orNull != "true") {
    include(":core:vision")
    if (providers.gradleProperty("visionOnly").orNull != "true") include(":app")
}
