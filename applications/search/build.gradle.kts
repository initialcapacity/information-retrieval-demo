plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":components:catalog"))
    implementation(project(":components:search"))
    implementation(project(":components:eval"))
    implementation(project(":components:starter-environment"))
    implementation(project(":components:database-support"))
    implementation(project(":components:web-support"))

    implementation(libs.bundles.app)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.jackson.databind)

    testImplementation(project(":components:test-support"))
}

tasks {
    shadowJar {
        manifest {
            attributes("Main-Class" to "Main")
        }
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    register<JavaExec>("run") {
        group = "ir-demo"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("Main")
        workingDir = rootProject.projectDir
    }
}
