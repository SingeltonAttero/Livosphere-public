plugins {
    alias(libs.plugins.android.library)
    id("livosphere.set-consumer")
}
setContract { setId.set("electric-harbor"); surface.set("clock-widget") }
android {
    namespace = "app.livosphere.sets.electric_harbor.clock_widget"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
