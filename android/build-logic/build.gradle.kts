plugins {
    `java-gradle-plugin`
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

// Nested --offline builds receive these plugins directly, with no plugin-marker cache dependency.
val testKitPlugins by configurations.creating
val testKitKotlinRuntime by configurations.creating

dependencies {
    implementation("com.android.tools.build:gradle-api:9.3.2")
    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.13.2")
    testKitPlugins("com.android.tools.build:gradle:9.3.2")
    testKitPlugins("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    testKitKotlinRuntime("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.3.21")
    testKitKotlinRuntime("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
    testKitKotlinRuntime("org.jetbrains.kotlin:kotlin-build-tools-impl:2.3.21")
}

gradlePlugin {
    plugins {
        register("wffResourceOnly") {
            id = "livosphere.wff-resource-only"
            implementationClass = "app.livosphere.buildlogic.WffResourceOnlyPlugin"
        }
        register("setConsumer") {
            id = "livosphere.set-consumer"
            implementationClass = "app.livosphere.buildlogic.SetConsumerPlugin"
        }
        register("setRegistry") {
            id = "livosphere.set-registry"
            implementationClass = "app.livosphere.buildlogic.SetRegistryPlugin"
        }
    }
}

// Compile the generated phone registry against the canonical pure Kotlin contract in TestKit.
tasks.test {
    val contractSource = file("../core/contract/src/main/kotlin/app/livosphere/contract/SetDescriptor.kt")
    inputs.file(contractSource)
    inputs.files(testKitPlugins, testKitKotlinRuntime)
    val contourRoot = file("../sets/contour")
    inputs.file(contourRoot.resolve("manifest/set.properties"))
    inputs.dir(contourRoot.resolve("source-assets"))
    systemProperty("livosphere.contourManifest", contourRoot.resolve("manifest/set.properties").absolutePath)
    systemProperty("livosphere.testKitHome", gradle.gradleUserHomeDir.absolutePath)
    doFirst {
        systemProperty("livosphere.testKitPluginClasspath", testKitPlugins.asPath)
    }
    systemProperty("livosphere.contractSource", contractSource.absolutePath)
}
