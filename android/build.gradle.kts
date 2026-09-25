import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.artifacts.ProjectDependency
import java.util.Properties

plugins {
    base
    id("livosphere.variant-source-collector")
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

val buildProfile = providers.gradleProperty("livosphere.buildProfile").orElse("phone").get()
check(buildProfile == "phone" || buildProfile == "legacy") {
    "Gradle property 'livosphere.buildProfile' supports only phone or legacy, got: $buildProfile"
}
val legacyProfile = buildProfile == "legacy"

private fun doctorErrors(javaFeature: Int, sdkRoot: File): List<String> = buildList {
    if (javaFeature != 17) {
        add("Требуется JDK 17, обнаружен JDK $javaFeature. Укажите JAVA_HOME на JDK 17.")
    }
    if (!sdkRoot.isDirectory) {
        add("Android SDK не найден: ${sdkRoot.absolutePath}")
    } else if (!sdkRoot.resolve("platforms/android-37/android.jar").isFile &&
        !sdkRoot.resolve("platforms/android-37.0/android.jar").isFile
    ) {
        add("Android SDK Platform 37 не установлена в ${sdkRoot.absolutePath}")
    }
}

tasks.register("doctor") {
    group = "verification"
    description = "Проверяет JDK и Android SDK для локальной сборки Livosphere."
    doLast {
        val sdkPath = providers.environmentVariable("ANDROID_SDK_ROOT")
            .orElse(providers.environmentVariable("ANDROID_HOME"))
            .orNull
            ?: error("ANDROID_SDK_ROOT или ANDROID_HOME не задан.")
        val errors = doctorErrors(Runtime.version().feature(), file(sdkPath))
        check(errors.isEmpty()) { errors.joinToString(separator = "\n") }
        logger.lifecycle("Livosphere doctor: JDK 17, Android SDK 37 — OK")
    }
}

tasks.register("testDoctorContract") {
    group = "verification"
    doLast {
        val fixture = layout.buildDirectory.dir("doctor-fixture-sdk").get().asFile
        fixture.resolve("platforms/android-37").mkdirs()
        fixture.resolve("platforms/android-37/android.jar").writeBytes(byteArrayOf())
        val emptySdk = layout.buildDirectory.dir("doctor-empty-sdk").get().asFile
        emptySdk.mkdirs()
        check(doctorErrors(17, fixture).isEmpty())
        check(doctorErrors(21, fixture).single().contains("JDK 17"))
        check(doctorErrors(17, fixture.resolve("missing")).single().contains("не найден"))
        check(doctorErrors(17, emptySdk).single().contains("Platform 37"))
    }
}

tasks.register("verifyProductRelease") {
    group = "verification"
    doLast {
        val productRelease = providers.gradleProperty("livosphere.productRelease").orNull
        check(!productRelease.isNullOrBlank()) {
            "Gradle property livosphere.productRelease обязателен как отдельный build input."
        }
        check(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(?:-[a-z0-9.-]+)?$").matches(productRelease)) {
            "livosphere.productRelease имеет неверный формат: $productRelease"
        }
        val phoneConfig = project(":hub:app").extensions.getByType(ApplicationExtension::class.java).defaultConfig
        check(phoneConfig.versionName == productRelease) {
            ":hub:app versionName=${phoneConfig.versionName} не совпадает с livosphere.productRelease=$productRelease"
        }
        check(phoneConfig.versionCode != null) { "Phone обязан сохранять собственный versionCode" }
        if (legacyProfile) {
            val watchConfig = project(":watchfaces:contour-wff").extensions
                .getByType(ApplicationExtension::class.java).defaultConfig
            check(watchConfig.versionName == productRelease) {
                ":watchfaces:contour-wff versionName=${watchConfig.versionName} не совпадает с livosphere.productRelease=$productRelease"
            }
            check(watchConfig.versionCode != null) { "WFF обязан сохранять собственный versionCode" }
        }
    }
}

tasks.register("assetsCheck") {
    group = "verification"
    description = "Проверяет manifest-driven contracts и assets выбранного build profile."
    val selectedTasks = mutableListOf(
        ":hub:app:validateSetRegistry",
        ":sets:contour:preview:validateSetContract",
        ":wallpapers:contour:validateSetContract",
        ":wallpapers:fixture:validateSetContract",
        ":sets:fixture:preview:validateSetContract",
        ":sets:fixture:clock-widget:validateSetContract",
    )
    if (legacyProfile) selectedTasks += ":watchfaces:contour-wff:validateSetContract"
    dependsOn(selectedTasks)
}

tasks.register("generateSetContracts") {
    group = "build"
    description = "Восстанавливает generated registry и resources всех surfaces."
    val selectedTasks = mutableListOf(
        ":hub:app:generateSetRegistry",
        ":sets:contour:preview:generateSetResources",
        ":wallpapers:contour:generateSetResources",
        ":wallpapers:fixture:generateSetResources",
        ":sets:fixture:preview:generateSetResources",
        ":sets:fixture:clock-widget:generateSetResources",
    )
    if (legacyProfile) selectedTasks += ":watchfaces:contour-wff:generateSetResources"
    dependsOn(selectedTasks)
}

// Gradle may discard resolved input dependency providers after executing a task.
// Capture the real package edges before execution; validate this immutable snapshot in check.
val packageDependencySnapshot = mutableMapOf<String, Set<String>>()
gradle.taskGraph.whenReady {
    subprojects.filter { it.buildFile.isFile }.forEach { module ->
        listOf("assembleDebug", "bundleDebug").forEach { name ->
            module.tasks.findByName(name)?.let { packageTask ->
                val visited = mutableSetOf<String>()
                fun visit(task: org.gradle.api.Task) {
                    if (visited.add(task.path)) task.taskDependencies.getDependencies(task).forEach(::visit)
                }
                visit(packageTask)
                packageDependencySnapshot[packageTask.path] = visited.toSet()
            }
        }
    }
}

tasks.register("verifyModuleGraph") {
    group = "verification"
    doLast {
        data class ManifestContribution(val projectPath: String, val surface: String, val schemaVersion: Int)

        val manifestProperties = providers.gradleProperty("livosphere.setManifests").get()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { path -> Properties().apply { file(path).inputStream().use(::load) } }
        val manifestContributions = manifestProperties.flatMap { properties ->
            val schemaVersion = properties.getProperty("schemaVersion").toInt()
            properties.getProperty("contributions").split(',').map(String::trim).map { contribution ->
                ManifestContribution(
                    projectPath = properties.getProperty("contribution.$contribution.artifactProject"),
                    surface = properties.getProperty("contribution.$contribution.surface"),
                    schemaVersion = schemaVersion,
                )
            }
        }
        val phoneArtifactProjects = manifestContributions
            .filter { it.surface != "watchface" }
            .map { it.projectPath }
            .toSet()
        val enabledArtifactProjects = manifestContributions
            .filter { it.surface != "watchface" || legacyProfile }
            .map { it.projectPath }
            .toSet()

        val expected = mutableMapOf(
            ":hub:app" to setOf(
                "implementation" to ":hub:domain",
                "implementation" to ":core:settings",
                "implementation" to ":wallpapers:engine",
                "implementation" to ":widgets:runtime",
                "testImplementation" to ":core:testing",
                "androidTestImplementation" to ":wallpapers:engine",
                "androidTestImplementation" to ":wallpapers:neon",
            ),
            ":hub:domain" to setOf("api" to ":core:contract"),
            ":core:settings" to setOf("api" to ":core:contract"),
            ":wallpapers:engine" to setOf("api" to ":core:contract"),
            ":widgets:runtime" to setOf("api" to ":core:contract", "implementation" to ":core:settings"),
            ":wallpapers:static" to setOf("api" to ":wallpapers:engine"),
            ":wallpapers:neon" to setOf("api" to ":wallpapers:engine", "implementation" to ":core:settings"),
            ":wallpapers:contour" to setOf("implementation" to ":wallpapers:engine", "implementation" to ":core:settings"),
            ":wallpapers:fixture" to setOf("implementation" to ":wallpapers:engine", "implementation" to ":core:settings"),
            ":quality:macrobenchmark" to setOf(
                "compileOnly" to ":hub:app",
                "testedApks" to ":hub:app",
            ),
        )
        expected[":hub:app"] = expected.getValue(":hub:app") +
            phoneArtifactProjects.map { "debugImplementation" to it }
        manifestContributions.forEach { contribution ->
            if (contribution.projectPath !in expected) {
                expected[contribution.projectPath] = when {
                    contribution.surface == "wallpaper" && contribution.schemaVersion >= 4 ->
                        setOf("implementation" to ":wallpapers:static")
                    contribution.surface == "wallpaper" -> setOf("implementation" to ":wallpapers:neon")
                    else -> emptySet()
                }
            }
        }

        val required = mutableSetOf(
            ":hub:app",
            ":hub:domain",
            ":core:contract",
            ":core:testing",
            ":core:settings",
            ":wallpapers:fixture",
            ":sets:fixture:preview",
            ":sets:fixture:clock-widget",
            ":wallpapers:engine",
            ":wallpapers:static",
            ":wallpapers:neon",
            ":wallpapers:contour",
            ":widgets:runtime",
            ":sets:contour:preview",
            ":quality:macrobenchmark",
        )
        required += enabledArtifactProjects
        check(required.all { findProject(it) != null }) { "Обязательный модуль отсутствует." }
        val configuredModules = subprojects.filter { it.buildFile.isFile }.map { it.path }.toSet()
        check(configuredModules == required) {
            "Набор модулей отличается от утверждённого: ${configuredModules.sorted()}"
        }
        required.forEach { path ->
            val actual = project(path).configurations
                .filter { configuration -> configuration.isCanBeDeclared }
                .flatMap { configuration ->
                    configuration.dependencies.withType(ProjectDependency::class.java)
                        .filter { dependency -> dependency.path != path }
                        .map { dependency -> configuration.name to dependency.path }
                }
                .toSet()
            val expectedForProject = expected.getOrDefault(path, emptySet())
            check(actual == expectedForProject) {
                "$path: ожидались зависимости $expectedForProject, обнаружены $actual"
            }
        }

        val composeProjects = subprojects.filter {
            it.pluginManager.hasPlugin("org.jetbrains.kotlin.plugin.compose")
        }.map { it.path }.toSet()
        val hiltProjects = subprojects.filter {
            it.pluginManager.hasPlugin("com.google.dagger.hilt.android")
        }.map { it.path }.toSet()
        check(composeProjects == setOf(":hub:app")) { "Compose разрешён только в :hub:app: $composeProjects" }
        check(hiltProjects == setOf(":hub:app")) { "Hilt разрешён только в :hub:app: $hiltProjects" }

        val hiltRoots = subprojects.flatMap { module ->
            module.fileTree("src") {
                include("**/*.kt", "**/*.java")
            }.filter { source -> source.readText().contains("@HiltAndroidApp") }
                .map { source -> module.path to source }
        }
        check(hiltRoots.size == 1 && hiltRoots.single().first == ":hub:app") {
            "Ожидался единственный @HiltAndroidApp в :hub:app, обнаружено: $hiltRoots"
        }

        val setConsumers = subprojects.filter {
            it.pluginManager.hasPlugin("livosphere.set-consumer")
        }.map { it.path }.toSet()
        val expectedSetConsumers = enabledArtifactProjects
        check(setConsumers == expectedSetConsumers) { "Set consumer conventions подключены неверно: $setConsumers" }
        check(project(":hub:app").pluginManager.hasPlugin("livosphere.set-registry")) {
            ":hub:app обязан получать registry через livosphere.set-registry"
        }

        val registryPackagePredecessors = mapOf(
            ":hub:app:assembleDebug" to listOf(":hub:app:validateDebugSetRegistry", ":hub:app:generateDebugSetRegistry"),
        )
        val consumerPackagePredecessors = manifestContributions
            .filter { it.projectPath in enabledArtifactProjects }
            .associate { contribution ->
                val packageTask = if (contribution.surface == "watchface") "bundleDebug" else "assembleDebug"
                "${contribution.projectPath}:$packageTask" to listOf(
                    "${contribution.projectPath}:validateDebugSetContract",
                    "${contribution.projectPath}:generateDebugSetResources",
                )
            }
        (registryPackagePredecessors + consumerPackagePredecessors).forEach { (packageTaskPath, requiredTasks) ->
            requiredTasks.forEach { required ->
                check(required in packageDependencySnapshot.getValue(packageTaskPath)) {
                    "$required обязан быть predecessor реального package path $packageTaskPath"
                }
            }
        }

        val coreContractSources = project(":core:contract").fileTree("src/main") {
            include("**/*.kt", "**/*.java")
        }
        val androidType = Regex("(?:^|[^A-Za-z0-9_])android\\.[a-z][A-Za-z0-9_.]*")
        check(coreContractSources.none { source ->
            androidType.containsMatchIn(source.readText())
        }) { ":core:contract не должен содержать Android API" }

        val manualRegistry = subprojects.flatMap { module ->
            module.fileTree("src") { include("**/*.kt", "**/*.java") }
                .filter { source ->
                    Regex("\\bimplements\\s+(?:app\\.livosphere\\.contract\\.)?SetRegistry\\b|" +
                        ":\\s*(?:app\\.livosphere\\.contract\\.)?SetRegistry\\b")
                        .containsMatchIn(source.readText())
                }
                .map { source -> module.path to source }
        }
        check(manualRegistry.isEmpty()) { "Обнаружен ручной SetRegistry: $manualRegistry" }

        val manifestPaths = providers.gradleProperty("livosphere.setManifests").get()
            .split(',').map(String::trim)
        val setPrefixes = manifestPaths.map { path ->
            val properties = Properties().apply { file(path).inputStream().use(::load) }
            "ls_${properties.getProperty("setId").replace('-', '_')}_"
        }.toSet()
        val manuallyOwnedSetResources = subprojects.flatMap { module ->
            module.fileTree("src") { include("**/*") }
                .filter { source ->
                    source.isFile && setPrefixes.any { prefix ->
                        source.name.startsWith(prefix) ||
                            (source.extension == "xml" && Regex("<[^>]+\\bname=\\\"$prefix")
                                .containsMatchIn(source.readText()))
                    }
                }
                .map { source -> module.path to source }
        }
        check(manuallyOwnedSetResources.isEmpty()) {
            "Set-owned resources должны поступать только из manifest generation: $manuallyOwnedSetResources"
        }
    }
}

tasks.named("check") {
    dependsOn(
        "testDoctorContract",
        "verifyProductRelease",
        "assetsCheck",
        "verifyModuleGraph",
        gradle.includedBuild("build-logic").task(":test"),
        ":hub:app:testDebugUnitTest",
        ":hub:app:lintDebug",
        ":hub:app:assembleDebug",
        ":hub:domain:test",
        ":core:contract:test",
        ":core:testing:test",
        ":core:settings:testDebugUnitTest",
        ":core:settings:lintDebug",
        ":wallpapers:fixture:lintDebug",
        ":sets:fixture:preview:lintDebug",
        ":sets:fixture:clock-widget:lintDebug",
        ":wallpapers:engine:testDebugUnitTest",
        ":wallpapers:engine:lintDebug",
        ":wallpapers:contour:testDebugUnitTest",
        ":wallpapers:contour:lintDebug",
        ":sets:contour:preview:lintDebug",
    )
    if (legacyProfile) dependsOn(
        ":watchfaces:contour-wff:check",
        ":watchfaces:contour-wff:verifyWffResourceOnly",
    )
}
