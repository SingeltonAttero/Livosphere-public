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

val buildProfile = providers.gradleProperty("livosphere.buildProfile").orElse("phone").get()
require(buildProfile == "phone" || buildProfile == "legacy") {
    "Gradle property 'livosphere.buildProfile' supports only phone or legacy, got: $buildProfile"
}

include(
    ":core:settings",
    ":wallpapers:fixture",
    ":sets:fixture:preview",
    ":sets:fixture:clock-widget",
    ":hub:app",
    ":hub:domain",
    ":core:contract",
    ":core:testing",
    ":wallpapers:engine",
    ":wallpapers:contour",
    ":widgets:runtime",
    ":sets:contour:preview",
    ":quality:macrobenchmark",
)

if (buildProfile == "legacy") {
    include(":watchfaces:contour-wff")
}

project(":hub:app").projectDir = file("hub/app")
project(":hub:domain").projectDir = file("hub/domain")
project(":core:contract").projectDir = file("core/contract")
project(":core:testing").projectDir = file("core/testing")
project(":wallpapers:engine").projectDir = file("wallpapers/engine")
project(":wallpapers:contour").projectDir = file("wallpapers/contour")
project(":widgets:runtime").projectDir = file("widgets/runtime")
project(":sets:contour:preview").projectDir = file("sets/contour/preview")
project(":quality:macrobenchmark").projectDir = file("quality/macrobenchmark")

if (buildProfile == "legacy") {
    project(":watchfaces:contour-wff").projectDir = file("watchfaces/contour-wff")
}

project(":core:settings").projectDir = file("core/settings")

project(":wallpapers:fixture").projectDir = file("wallpapers/fixture")

project(":sets:fixture:preview").projectDir = file("sets/fixture/preview")

project(":sets:fixture:clock-widget").projectDir = file("sets/fixture/clock-widget")

include(":wallpapers:neon")
include(":sets:night-sakura:preview")
include(":sets:night-sakura:wallpaper")
include(":sets:night-sakura:clock-widget")
include(":sets:electric-harbor:preview")
include(":sets:electric-harbor:wallpaper")
include(":sets:electric-harbor:clock-widget")
include(":sets:last-light:preview")
include(":sets:last-light:wallpaper")
include(":sets:last-light:clock-widget")
