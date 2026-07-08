plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":components:database-support"))
    implementation(libs.slf4j.simple)
    implementation(libs.postgresql)
    implementation(libs.flyway.postgres)
    implementation(libs.flyway.core)
}

tasks {
    shadowJar {
        manifest {
            attributes("Main-Class" to "Main")
        }

        mergeServiceFiles()
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    register<JavaExec>("migrateDev") {
        classpath = files(shadowJar)
        environment("DATABASE_URL", "jdbc:postgresql://localhost:5433/dubjug_development?user=postgres&password=postgres")
    }

    register<JavaExec>("migrateTest") {
        classpath = files(shadowJar)
        environment("DATABASE_URL", "jdbc:postgresql://localhost:5433/dubjug_test?user=postgres&password=postgres")
    }

    register("migrate") {
        dependsOn(named("migrateDev"), named("migrateTest"))
    }
}
