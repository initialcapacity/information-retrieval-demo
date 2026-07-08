dependencies {
    implementation(project(":components:starter-environment"))
    implementation(project(":components:database-support"))
    implementation(project(":components:web-support"))

    implementation(libs.jackson.databind)
    implementation(libs.hikari)
    implementation(libs.junit.jupiter)
    implementation(libs.slf4j.simple)
    implementation(libs.javalin.core)
}
