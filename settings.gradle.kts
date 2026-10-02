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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Generate lockfile for dependency verification:
//   ./gradlew --write-locks
// Then commit gradle/verification-metadata.xml
rootProject.name = "DrywallMaterialsCalculator"
include(":app")
include(":keygen")
include(":common")
include(":cleaner")
