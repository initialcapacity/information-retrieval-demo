dependencies {
    implementation(project(":components:database-support"))
    implementation(libs.jackson.databind)

    testImplementation(project(":components:test-support"))
    testImplementation(libs.postgresql)
}
