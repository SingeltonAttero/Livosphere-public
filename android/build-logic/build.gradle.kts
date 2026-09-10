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
        register("variantSourceCollector") {
            id = "livosphere.variant-source-collector"
            implementationClass = "app.livosphere.buildlogic.AndroidVariantSourceCollectorPlugin"
        }
    }
}

val contractSource = file("../core/contract/src/main/kotlin/app/livosphere/contract/SetDescriptor.kt")

fun Test.configureBuildLogicFixtureEnvironment(evidenceDirectory: String) {
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
    val packagingEvidence = layout.buildDirectory.dir(evidenceDirectory)
    outputs.dir(packagingEvidence)
    systemProperty("livosphere.packagingEvidence", packagingEvidence.get().asFile.absolutePath)
}

// The default test lane is intentionally pure JVM: it never starts nested Gradle builds.
tasks.test {
    description = "Runs fast phone-profile and variant-selection unit tests without GradleRunner or APK inspection."
    filter.includeTestsMatching("app.livosphere.buildlogic.PhoneProfileUnitTest")
    filter.includeTestsMatching("app.livosphere.buildlogic.VariantSelectionUnitTest")
    configureBuildLogicFixtureEnvironment("reports/unit-selection")
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs the selected phone-profile or Story 8.4 theme wiring scenarios."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter.includeTestsMatching("app.livosphere.buildlogic.PhoneProfileIntegrationTest")
    filter.includeTestsMatching("app.livosphere.buildlogic.ThemeBuildIntegrationTest")
    shouldRunAfter(tasks.test)
    configureBuildLogicFixtureEnvironment("reports/theme-build-integration")
}

tasks.register<Test>("legacyAuditTest") {
    group = "legacy verification"
    description = "Explicit opt-in for historical APK/source inventory tests; never part of check."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter.excludeTestsMatching("app.livosphere.buildlogic.VariantSelectionUnitTest")
    filter.excludeTestsMatching("app.livosphere.buildlogic.PhoneProfileUnitTest")
    filter.excludeTestsMatching("app.livosphere.buildlogic.PhoneProfileIntegrationTest")
    filter.excludeTestsMatching("app.livosphere.buildlogic.ThemeBuildIntegrationTest")
    configureBuildLogicFixtureEnvironment("reports/legacy-variant-packaging")
}
