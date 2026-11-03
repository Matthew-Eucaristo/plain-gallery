pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "plain-gallery"

include(":app")
include(":baselineprofile")
include(":core:model")
include(":core:data")
include(":core:ui")
include(":core:faces")
include(":core:text")
include(":feature:library")
include(":feature:viewer")
include(":feature:search")
include(":feature:tidy")
include(":feature:settings")
include(":feature:editor")
include(":feature:create")
