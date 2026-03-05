dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("io.micrometer:micrometer-core")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Test dependencies already included via root allprojects block
// Explicit additions for this module:
dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
