plugins { alias(libs.plugins.android.library) }
android {
    namespace = "app.livosphere.wallpapers.neon"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    api(project(":wallpapers:engine"))
    implementation(project(":core:settings"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
}
