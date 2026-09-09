plugins {
    alias(libs.plugins.android.library)
    id("livosphere.set-consumer")
}
setContract {
    setId.set("isolation-fixture")
    surface.set("wallpaper")
}
android {
    namespace = "app.livosphere.wallpapers.fixture"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":wallpapers:engine"))
    implementation(project(":core:settings"))
    implementation(libs.kotlinx.coroutines.core)
}
