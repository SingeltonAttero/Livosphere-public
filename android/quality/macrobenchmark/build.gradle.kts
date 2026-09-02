plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "app.livosphere.quality.macrobenchmark"
    compileSdk = 37
    targetProjectPath = ":hub:app"

    defaultConfig {
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
