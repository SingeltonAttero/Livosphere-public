plugins {
    alias(libs.plugins.android.library)
    id("livosphere.set-consumer")
}
setContract { setId.set("synthetic-dawn"); surface.set("wallpaper") }
android {
    namespace = "app.livosphere.sets.synthetic_dawn.wallpaper"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation(project(":wallpapers:static")) }
