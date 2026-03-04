plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":banking-common"))
    implementation(project(":banking-onboarding"))
    implementation(project(":banking-account"))
    implementation(project(":banking-payment"))
    implementation(project(":banking-fraud"))
    implementation(project(":banking-notification"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Spring AI — OpenAI + MCP
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")
    implementation("org.springframework.ai:spring-ai-mcp-core")
    implementation("org.springframework.ai:spring-ai-mcp-spring-webmvc")
    implementation("org.springframework.ai:spring-ai-core")

    // Observability
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Jackson
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // H2 for demo (replace with PostgreSQL in production)
    runtimeOnly("com.h2database:h2")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.h2database:h2")
}
