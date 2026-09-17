plugins {
    alias(libs.plugins.android.library)
    id("livosphere.set-consumer")
}
setContract { setId.set("night-sakura"); surface.set("clock-widget") }
android {
    namespace = "app.livosphere.sets.night_sakura.clock_widget"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
