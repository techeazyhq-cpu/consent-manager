import org.gradle.api.tasks.testing.Test

plugins {
    id("java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Testcontainers 1.20's docker-java asks for Docker API 1.32 by default; Docker Engine 29+ refuses anything
    // below 1.40 (400 on /info: "Could not find a valid Docker environment"). 1.44 works with Engine 25 and newer.
    systemProperty("api.version", "1.44")
}

// Container image name/tag every docker-compose.yml service reference expects (plan §6:
// Buildpacks, not a hand-written Dockerfile) — one place to keep this consistent instead of
// repeating it per service.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
    imageName.set("techeazyhq/${project.name}:${project.version}")
}
