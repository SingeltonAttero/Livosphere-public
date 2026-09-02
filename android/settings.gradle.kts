pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Livosphere"

include(
    ":hub:app",
    ":hub:domain",
    ":core:contract",
    ":core:testing",
    ":wallpapers:engine",
    ":wallpapers:contour",
    ":watchfaces:contour-wff",
    ":sets:contour:preview",
    ":quality:macrobenchmark",
)

project(":hub:app").projectDir = file("hub/app")
project(":hub:domain").projectDir = file("hub/domain")
project(":core:contract").projectDir = file("core/contract")
project(":core:testing").projectDir = file("core/testing")
project(":wallpapers:engine").projectDir = file("wallpapers/engine")
project(":wallpapers:contour").projectDir = file("wallpapers/contour")
project(":watchfaces:contour-wff").projectDir = file("watchfaces/contour-wff")
project(":sets:contour:preview").projectDir = file("sets/contour/preview")
project(":quality:macrobenchmark").projectDir = file("quality/macrobenchmark")
