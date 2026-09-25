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
    ":wallpapers:static",
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
project(":wallpapers:static").projectDir = file("wallpapers/static")
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
include(":sets:moscow-facets:preview")
include(":sets:moscow-facets:wallpaper")
include(":sets:moscow-facets:clock-widget")
include(":sets:synthetic-dawn:preview")
include(":sets:synthetic-dawn:wallpaper")
include(":sets:synthetic-dawn:clock-widget")
include(":sets:neon-express:preview")
include(":sets:neon-express:wallpaper")
include(":sets:neon-express:clock-widget")
include(":sets:rainforest:preview")
include(":sets:rainforest:wallpaper")
include(":sets:rainforest:clock-widget")
include(":sets:emerald-cove:preview")
include(":sets:emerald-cove:wallpaper")
include(":sets:emerald-cove:clock-widget")
include(":sets:orbital-window:preview")
include(":sets:orbital-window:wallpaper")
include(":sets:orbital-window:clock-widget")
include(":sets:star-river:preview")
include(":sets:star-river:wallpaper")
include(":sets:star-river:clock-widget")
include(":sets:golden-dunes:preview")
include(":sets:golden-dunes:wallpaper")
include(":sets:golden-dunes:clock-widget")
include(":sets:moon-tide:preview")
include(":sets:moon-tide:wallpaper")
include(":sets:moon-tide:clock-widget")
include(":sets:chromatic-flow:preview")
include(":sets:chromatic-flow:wallpaper")
include(":sets:chromatic-flow:clock-widget")
