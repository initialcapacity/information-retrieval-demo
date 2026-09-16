dependencies {
    implementation(project(":components:search"))
    implementation(project(":components:database-support"))

    testImplementation(project(":components:test-support"))
    testImplementation(libs.postgresql)
}
