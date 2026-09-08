plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("livosphere.set-registry")
}

android {
    namespace = "app.livosphere"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.livosphere"
        minSdk = 29
        targetSdk = 36
        versionCode = providers.gradleProperty("livosphere.phoneVersionCode")
            .map(String::toInt)
            .orElse(1)
            .get()
        versionName = providers.gradleProperty("livosphere.productRelease").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    /**
     * Locally installable SP-06 target: profileable by manifest, not debuggable at runtime.
     * It retains debug signing only so an owner can install the exact measurement APK locally.
     */
    buildTypes {
        release {
            val keystore = System.getenv("LIVOSPHERE_RELEASE_KEYSTORE")
            val storePassword = System.getenv("LIVOSPHERE_RELEASE_STORE_PASSWORD")
            val keyAlias = System.getenv("LIVOSPHERE_RELEASE_KEY_ALIAS")
            val keyPassword = System.getenv("LIVOSPHERE_RELEASE_KEY_PASSWORD")
            if (listOf(keystore, storePassword, keyAlias, keyPassword).all { !it.isNullOrBlank() }) {
                signingConfig = signingConfigs.create("livosphereRelease") {
                    storeFile = file(keystore!!)
                    this.storePassword = storePassword
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPassword
                }
            }
        }
        create("benchmark") {
            initWith(getByName("release"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release", "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":hub:domain"))
    implementation(project(":wallpapers:contour"))

    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(project(":wallpapers:engine"))
    debugImplementation(libs.compose.ui.test.manifest)
}
