dependencies {
    implementation(libs.hikari)

    testImplementation(project(":components:test-support"))
    testImplementation(libs.postgresql)
}