dependencies {
    implementation(project(":components:database-support"))

    implementation(libs.hikari)
    implementation(libs.slf4j.simple)
}
