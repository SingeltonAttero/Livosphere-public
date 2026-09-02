plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.livosphere.wallpapers.engine"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:contract"))
    testImplementation(libs.junit4)
}
