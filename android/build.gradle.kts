import org.gradle.api.artifacts.ProjectDependency

plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

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

tasks.register("verifyModuleGraph") {
    group = "verification"
    doLast {
        val expected = mapOf(
            ":hub:app" to setOf(
                "implementation" to ":hub:domain",
                "implementation" to ":wallpapers:contour",
                "implementation" to ":sets:contour:preview",
                "testImplementation" to ":core:testing",
            ),
            ":hub:domain" to setOf("api" to ":core:contract"),
            ":wallpapers:engine" to setOf("api" to ":core:contract"),
            ":wallpapers:contour" to setOf("implementation" to ":wallpapers:engine"),
            ":quality:macrobenchmark" to setOf(
                "compileOnly" to ":hub:app",
                "testedApks" to ":hub:app",
            ),
        )
        val required = listOf(
            ":hub:app",
            ":hub:domain",
            ":core:contract",
            ":core:testing",
            ":wallpapers:engine",
            ":wallpapers:contour",
            ":watchfaces:contour-wff",
            ":sets:contour:preview",
            ":quality:macrobenchmark",
        )
        check(required.all { findProject(it) != null }) { "Обязательный модуль отсутствует." }
        val configuredModules = subprojects.filter { it.buildFile.isFile }.map { it.path }.toSet()
        check(configuredModules == required.toSet()) {
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
    }
}

tasks.named("check") {
    dependsOn(
        "testDoctorContract",
        "verifyModuleGraph",
        ":hub:app:check",
        ":hub:domain:check",
        ":core:contract:check",
        ":core:testing:check",
        ":wallpapers:engine:check",
        ":wallpapers:contour:check",
        ":watchfaces:contour-wff:check",
        ":watchfaces:contour-wff:verifyWffResourceOnly",
        ":sets:contour:preview:check",
        ":quality:macrobenchmark:check",
    )
}
