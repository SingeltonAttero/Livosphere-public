plugins {
    alias(libs.plugins.android.library)
    id("livosphere.set-consumer")
}
setContract { setId.set("last-light"); surface.set("clock-widget") }
android {
    namespace = "app.livosphere.sets.last_light.clock_widget"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
