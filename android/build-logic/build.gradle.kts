plugins {
    `java-gradle-plugin`
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

dependencies {
    compileOnly("com.android.tools.build:gradle-api:9.3.2")
}

gradlePlugin {
    plugins {
        register("wffResourceOnly") {
            id = "livosphere.wff-resource-only"
            implementationClass = "app.livosphere.buildlogic.WffResourceOnlyPlugin"
        }
    }
}
