dependencies {
    implementation(project(":components:database-support"))

    testImplementation(project(":components:test-support"))
    testImplementation(libs.postgresql)
}
