plugins {
    `java-gradle-plugin`
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

dependencies {
    implementation("com.android.tools.build:gradle-api:9.3.2")
    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.13.2")
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
