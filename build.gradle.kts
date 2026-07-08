import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL

plugins {
    id("java")
}

repositories {
    mavenCentral()
}

var libraries = libs

subprojects {
    if (listOf("components", "applications", "databases").contains(name)) return@subprojects

    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    dependencies {
        implementation(libraries.bundles.java)
        testImplementation(libraries.bundles.test)
        testRuntimeOnly(libraries.bundles.testRuntime)
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(26)
        }
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
        testLogging {
            events("failed")
            exceptionFormat = FULL
        }
    }
}
