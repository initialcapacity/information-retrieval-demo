plugins {
    application
}

dependencies {
    implementation(project(":components:catalog"))
    implementation(project(":components:search"))
    implementation(project(":components:eval"))
    implementation(project(":components:database-support"))
    implementation(project(":components:starter-environment"))

    implementation(libs.slf4j.simple)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.jackson.databind)

    testImplementation(project(":components:test-support"))
}

fun registerTool(taskName: String, main: String) {
    tasks.register<JavaExec>(taskName) {
        group = "ir-demo"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set(main)
        workingDir = rootProject.projectDir
    }
}

registerTool("ingestDocs", "io.ic.starter.tools.IngestMain")
registerTool("backfillEmbeddings", "io.ic.starter.tools.BackfillMain")
registerTool("cacheQueryEmbeddings", "io.ic.starter.tools.CacheQueryEmbeddingsMain")
registerTool("runEval", "io.ic.starter.tools.EvalMain")
registerTool("smoke", "io.ic.starter.tools.SmokeMain")
