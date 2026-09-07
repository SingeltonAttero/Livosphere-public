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

tasks.register("verifySp06Setup") {
    group = "verification"
    description = "Assembles and validates the non-debuggable profileable artifact used for SP-06 capture."
    dependsOn(":hub:app:assembleBenchmark")
    val packagedManifest = rootProject.file(
        "hub/app/build/intermediates/packaged_manifests/benchmark/processBenchmarkManifestForPackage/AndroidManifest.xml")
    val targetApk = rootProject.file("hub/app/build/outputs/apk/benchmark/app-benchmark.apk")
    inputs.files(packagedManifest, targetApk)
    doLast {
        check(targetApk.isFile && targetApk.length() > 0) {
            "SP-06 requires an assembled target APK: ${targetApk.absolutePath}"
        }
        check(packagedManifest.isFile) {
            "SP-06 requires the manifest packaged with that APK: ${packagedManifest.absolutePath}"
        }
        check(packagedManifest.readText().contains("<profileable android:shell=\"true\"")) {
            "The assembled SP-06 target must retain its profileable shell seam after manifest merging."
        }
        check(!packagedManifest.readText().contains("android:debuggable=\"true\"")) {
            "The assembled SP-06 target must be non-debuggable."
        }
    }
}
