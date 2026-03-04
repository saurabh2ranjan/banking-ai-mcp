plugins {
    java
    id("org.springframework.boot")         version "3.4.1" apply false
    id("io.spring.dependency-management")  version "1.1.7" apply false
    id("org.owasp.dependencycheck")        version "10.0.4" apply false
}

extra["springAiVersion"]   = "1.0.0-M6"
extra["lombokVersion"]     = "1.18.36"
extra["mapstructVersion"]  = "1.6.3"
extra["testcontainersVer"] = "1.20.4"

allprojects {
    group   = "com.banking"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    dependencyManagement {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
            mavenBom("org.springframework.ai:spring-ai-bom:${rootProject.extra["springAiVersion"]}")
            mavenBom("org.testcontainers:testcontainers-bom:${rootProject.extra["testcontainersVer"]}")
        }
    }

    dependencies {
        // Lombok
        val lombokVersion = rootProject.extra["lombokVersion"] as String
        compileOnly("org.projectlombok:lombok:$lombokVersion")
        annotationProcessor("org.projectlombok:lombok:$lombokVersion")
        testCompileOnly("org.projectlombok:lombok:$lombokVersion")
        testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

        // MapStruct
        val mapstructVersion = rootProject.extra["mapstructVersion"] as String
        implementation("org.mapstruct:mapstruct:$mapstructVersion")
        annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
        // Lombok + MapStruct ordering fix
        annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

        // Test
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.assertj:assertj-core")
        testImplementation("org.mockito:mockito-core")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs("-XX:+EnableDynamicAgentLoading")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf(
            "-Amapstruct.defaultComponentModel=spring",
            "-parameters"
        ))
    }
}
