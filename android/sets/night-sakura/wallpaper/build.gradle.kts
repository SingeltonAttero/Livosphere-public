plugins {
    alias(libs.plugins.android.library)
    id("livosphere.set-consumer")
}
setContract { setId.set("night-sakura"); surface.set("wallpaper") }
android {
    namespace = "app.livosphere.sets.night_sakura.wallpaper"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation(project(":wallpapers:neon")) }
